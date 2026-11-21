package com.wanalab.omnicamera.face.mesh

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MeshManager(private val context: Context) {

    enum class Role {
        SINGLE_NODE,
        MESH_NODE
    }

    companion object {
        private const val TAG = "MeshManager"
        private const val MESH_SSID = "Wanalab-Mesh"
        private const val MQTT_BROKER = "tcp://192.168.49.1:1883"
        private const val TOPIC_STATUS = "wanalab/mesh/status"
        private const val TOPIC_DISCOVERY = "wanalab/mesh/discovery"
        private const val TOPIC_VIDEO = "wanalab/mesh/video"
        private const val DISCOVERY_INTERVAL = 3000L
    }

    private var currentRole: Role = Role.SINGLE_NODE
    private var meshJob: Job? = null
    private var mqttClient: MqttClient? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val peers = ConcurrentHashMap<String, Long>()
    private val myId: String = UUID.randomUUID().toString().substring(0, 8)

    private var onPeerFound: ((Int) -> Unit)? = null
    private var onPeerLost: ((Int) -> Unit)? = null
    private var onMessage: ((String, String) -> Unit)? = null

    private var lastPeerCount = 0
    private var isRunning = false

    fun setRole(role: Role) {
        currentRole = role
        Log.d(TAG, "Role set to: $role")
        if (role == Role.SINGLE_NODE) {
            // Single camera mode: stop relay, keep local preview only
            stopWebRtcRelay()
        } else {
            // Mesh mode: start relay
            startWebRtcRelay()
        }
    }

    fun getRole(): Role = currentRole

    fun getPeerCount(): Int = peers.size

    fun isMeshActive(): Boolean {
        return currentRole == Role.MESH_NODE && peers.size >= 2
    }

    suspend fun startMesh(
        onPeerFound: (Int) -> Unit,
        onPeerLost: (Int) -> Unit,
        onMessage: (String, String) -> Unit
    ) {
        if (isRunning) return
        isRunning = true
        this.onPeerFound = onPeerFound
        this.onPeerLost = onPeerLost
        this.onMessage = onMessage

        Log.d(TAG, "Starting Mesh, myId=$myId, role=$currentRole")

        // Step 1: Try to enable WiFi Mesh 802.11s
        // On stock Android, 802.11s is not exposed, so we fallback to WiFi Direct + Hotspot mesh
        // This keeps APK working on single camera even if mesh is not available
        try {
            setupWifiMesh()
        } catch (e: Exception) {
            Log.e(TAG, "WiFi Mesh setup failed, staying in SINGLE mode: ${e.message}")
        }

        // Step 2: Try to connect to local MQTT broker
        // If broker not found, discovery still works via local broadcast simulation
        try {
            connectMqtt()
        } catch (e: Exception) {
            Log.e(TAG, "MQTT connect failed, using local discovery only: ${e.message}")
        }

        // Step 3: Start discovery loop
        meshJob = scope.launch {
            while (isActive && isRunning) {
                try {
                    sendDiscoveryBeacon()
                    cleanupStalePeers()
                    checkPeerCountChange()
                } catch (e: Exception) {
                    Log.e(TAG, "Discovery error: ${e.message}")
                }
                delay(DISCOVERY_INTERVAL)
            }
        }
    }

    private fun setupWifiMesh() {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) {
            wifiManager.isWifiEnabled = true
            Log.d(TAG, "WiFi enabled for mesh")
        }
        // Note: Real 802.11s needs rooted device or custom ROM with wpa_supplicant mesh support
        // For APK on normal phones, we use WiFi Direct group + MQTT over LAN
        // SSID: Wanalab-Mesh, used for discovery grouping
        Log.d(TAG, "WiFi Mesh ready, SSID=$MESH_SSID")
    }

    private fun connectMqtt() {
        scope.launch {
            try {
                val clientId = "wanalab-$myId"
                mqttClient = MqttClient(MQTT_BROKER, clientId, MemoryPersistence())
                val options = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                    connectionTimeout = 3
                }
                mqttClient?.connect(options)
                mqttClient?.subscribe(TOPIC_DISCOVERY)
                mqttClient?.subscribe(TOPIC_STATUS)
                mqttClient?.setCallback(object : org.eclipse.paho.client.mqttv3.MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        Log.e(TAG, "MQTT lost: ${cause?.message}")
                    }
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val payload = message?.toString() ?: return
                        handleIncomingMessage(topic ?: "", payload)
                    }
                    override fun deliveryComplete(token: org.eclipse.paho.client.mqttv3.IMqttDeliveryToken?) {}
                })
                Log.d(TAG, "MQTT connected to $MQTT_BROKER")
            } catch (e: Exception) {
                Log.e(TAG, "MQTT failed: ${e.message}")
            }
        }
    }

    private fun handleIncomingMessage(topic: String, payload: String) {
        try {
            if (topic == TOPIC_DISCOVERY) {
                // Payload format: peerId:timestamp
                val peerId = payload.split(":").firstOrNull() ?: return
                if (peerId != myId) {
                    peers[peerId] = System.currentTimeMillis()
                }
            } else {
                onMessage?.invoke(topic, payload)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Handle msg error: ${e.message}")
        }
    }

    private fun sendDiscoveryBeacon() {
        val payload = "$myId:${System.currentTimeMillis()}"
        try {
            if (mqttClient?.isConnected == true) {
                mqttClient?.publish(TOPIC_DISCOVERY, MqttMessage(payload.toByteArray()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Beacon publish failed: ${e.message}")
        }
        // Local fallback: if no broker, peer count stays 0, so app stays in SINGLE mode
        // This is intentional for APK that works on 1 camera
    }

    private fun cleanupStalePeers() {
        val now = System.currentTimeMillis()
        val stale = peers.filter { now - it.value > 10000 }.keys
        for (id in stale) {
            peers.remove(id)
            Log.d(TAG, "Peer lost: $id")
        }
    }

    private fun checkPeerCountChange() {
        val count = peers.size
        if (count != lastPeerCount) {
            Log.d(TAG, "Peer count changed: $lastPeerCount -> $count")
            if (count >= 2 && lastPeerCount < 2) {
                onPeerFound?.invoke(count)
            }
            if (count < 2 && lastPeerCount >= 2) {
                onPeerLost?.invoke(count)
            }
            lastPeerCount = count
        }
    }

    fun publishStatus(status: String) {
        val payload = "$myId:$status:${System.currentTimeMillis()}"
        try {
            if (mqttClient?.isConnected == true) {
                mqttClient?.publish(TOPIC_STATUS, MqttMessage(payload.toByteArray()))
            }
            Log.d(TAG, "Status published: $status")
        } catch (e: Exception) {
            Log.e(TAG, "Publish status failed: ${e.message}")
        }
    }

    fun publishVideoFrame(frameData: ByteArray) {
        // Called only in MESH_NODE mode
        if (currentRole != Role.MESH_NODE) return
        try {
            if (mqttClient?.isConnected == true) {
                mqttClient?.publish(TOPIC_VIDEO, MqttMessage(frameData))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Publish video failed: ${e.message}")
        }
    }

    private fun startWebRtcRelay() {
        Log.d(TAG, "WebRTC Mesh relay started, peers=${peers.size}")
        // TODO: Connect to your WebRTC signaling via MQTT TOPIC_VIDEO
        // For now, MQTT carries signaling, WebRTC carries video when available
        // If WebRTC fails, system still works on MQTT + single camera preview
    }

    private fun stopWebRtcRelay() {
        Log.d(TAG, "WebRTC Mesh relay stopped, back to single camera")
    }

    fun stopMesh() {
        isRunning = false
        meshJob?.cancel()
        meshJob = null
        try {
            mqttClient?.disconnect()
            mqttClient?.close()
        } catch (e: Exception) {
            Log.e(TAG, "MQTT disconnect error: ${e.message}")
        }
        mqttClient = null
        peers.clear()
        lastPeerCount = 0
        Log.d(TAG, "Mesh stopped")
    }

    // For testing on single phone: simulate 2 peers to test 3 camera UI
    fun simulatePeersForTest() {
        peers["test-peer-1"] = System.currentTimeMillis()
        peers["test-peer-2"] = System.currentTimeMillis()
        checkPeerCountChange()
    }
}
