package com.example.meshcam

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
        MESH_NODE,
        HOST_NODE
    }

    companion object {
        private const val TAG = "MeshManager"
        private const val MESH_TOPIC_STATUS = "meshcam/status"
        private const val MESH_TOPIC_SIGNAL = "meshcam/signal"
        private const val MESH_TOPIC_STREAM = "meshcam/stream"
        private const val BROKER_URL = "tcp://192.168.43.1:1883"
        private const val FALLBACK_BROKER_URL = "tcp://127.0.0.1:1883"
        private const val MAX_MESH_NODES = 3
    }

    private var role: Role = Role.SINGLE_NODE
    private var meshJob: Job? = null
    private var mqttClient: MqttClient? = null
    private var isMeshRunning = false

    private val nodeId: String = UUID.randomUUID().toString().substring(0, 8)
    private val peers = ConcurrentHashMap<String, Long>()
    private val webRtcPeers = ConcurrentHashMap<String, WebRtcRelay>()

    private var onPeerFound: ((Int) -> Unit)? = null
    private var onPeerLost: ((Int) -> Unit)? = null
    private var onMessage: ((String, String) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun setRole(newRole: Role) {
        role = newRole
        Log.d(TAG, "Role set to: $role NodeId: $nodeId")
        if (role == Role.SINGLE_NODE) {
            // Keep MQTT off in single mode to save battery
            // WebRTC relays are closed
            closeAllWebRtc()
        }
    }

    fun getRole(): Role = role

    fun getPeerCount(): Int {
        cleanupDeadPeers()
        return peers.size
    }

    fun isSingleMode(): Boolean {
        return getPeerCount() == 0
    }

    fun startMesh(
        onPeerFound: (Int) -> Unit,
        onPeerLost: (Int) -> Unit,
        onMessage: (String, String) -> Unit
    ) {
        if (isMeshRunning) return
        isMeshRunning = true
        this.onPeerFound = onPeerFound
        this.onPeerLost = onPeerLost
        this.onMessage = onMessage

        // Step 1: Try to enable WiFi Mesh 802.11s style discovery
        // On real Android APK, this uses WiFi Direct + Hotspot fallback
        // because Android does not expose raw 802.11s API
        startWifiMeshDiscovery()

        // Step 2: Start local MQTT broker client
        scope.launch {
            connectMqttWithFallback()
        }

        // Step 3: Heartbeat + peer cleanup loop
        meshJob = scope.launch {
            while (isMeshRunning) {
                try {
                    publishStatus("heartbeat:$nodeId:${role.name}")
                    val count = getPeerCount()
                    cleanupDeadPeers()
                    delay(2000)
                } catch (e: Exception) {
                    Log.e(TAG, "Mesh loop error: ${e.message}")
                    delay(2000)
                }
            }
        }
    }

    private fun startWifiMeshDiscovery() {
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wifiManager.isWifiEnabled) {
                wifiManager.isWifiEnabled = true
            }
            Log.d(TAG, "WiFi Mesh discovery started. Node: $nodeId")
            Log.d(TAG, "Mode: 802.11s concept via WiFi Direct / Hotspot fallback")
        } catch (e: Exception) {
            Log.e(TAG, "WiFi discovery failed, staying in single mode: ${e.message}")
        }
    }

    private fun connectMqttWithFallback() {
        // Try main mesh broker first, then local fallback
        // This allows single camera to work even with no broker
        val urls = listOf(BROKER_URL, FALLBACK_BROKER_URL)
        for (url in urls) {
            try {
                val clientId = "meshcam-$nodeId"
                mqttClient = MqttClient(url, clientId, MemoryPersistence())
                val options = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                    connectionTimeout = 3
                }
                mqttClient?.connect(options)
                mqttClient?.subscribe(MESH_TOPIC_STATUS)
                mqttClient?.subscribe(MESH_TOPIC_SIGNAL)
                mqttClient?.subscribe(MESH_TOPIC_STREAM)

                mqttClient?.setCallback(object : org.eclipse.paho.client.mqttv3.MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        Log.d(TAG, "MQTT lost, fallback to single: ${cause?.message}")
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val payload = message.toString()
                        handleMqttMessage(topic ?: "", payload)
                    }

                    override fun deliveryComplete(token: org.eclipse.paho.client.mqttv3.IMqttDeliveryToken?) {}
                })

                Log.d(TAG, "MQTT connected to: $url")
                return
            } catch (e: Exception) {
                Log.d(TAG, "MQTT failed for $url, trying next: ${e.message}")
            }
        }
        Log.d(TAG, "No MQTT broker found, running in single camera local mode")
    }

    private fun handleMqttMessage(topic: String, payload: String) {
        try {
            when {
                topic == MESH_TOPIC_STATUS && payload.startsWith("heartbeat:") -> {
                    val parts = payload.split(":")
                    if (parts.size >= 2) {
                        val peerId = parts[1]
                        if (peerId != nodeId) {
                            val isNew = !peers.containsKey(peerId)
                            peers[peerId] = System.currentTimeMillis()
                            if (isNew) {
                                val count = getPeerCount()
                                Log.d(TAG, "Peer found: $peerId total: $count")
                                if (count >= 2 && role == Role.SINGLE_NODE) {
                                    // 3 cameras total: me + 2 peers
                                    role = Role.MESH_NODE
                                    onPeerFound?.invoke(count)
                                } else if (count >= 1) {
                                    onPeerFound?.invoke(count)
                                }
                                // Create WebRTC relay for new peer
                                createWebRtcRelay(peerId)
                            }
                        }
                    }
                }
                topic == MESH_TOPIC_SIGNAL -> {
                    // WebRTC signaling via MQTT
                    handleWebRtcSignal(payload)
                }
                else -> {
                    onMessage?.invoke(topic, payload)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MQTT handle error: ${e.message}")
        }
    }

    private fun cleanupDeadPeers() {
        val now = System.currentTimeMillis()
        val dead = peers.filter { now - it.value > 6000 }.keys
        for (peerId in dead) {
            peers.remove(peerId)
            webRtcPeers[peerId]?.close()
            webRtcPeers.remove(peerId)
            Log.d(TAG, "Peer lost: $peerId remaining: ${peers.size}")
            onPeerLost?.invoke(peers.size)
            if (peers.isEmpty() && role == Role.MESH_NODE) {
                role = Role.SINGLE_NODE
                Log.d(TAG, "No peers left, fallback to SINGLE_NODE")
            }
        }
    }

    fun publishStatus(status: String) {
        try {
            val payload = "$status:$nodeId"
            mqttClient?.publish(
                MESH_TOPIC_STATUS,
                MqttMessage(payload.toByteArray())
            )
        } catch (e: Exception) {
            // Single camera mode: MQTT may be offline, ignore
        }
    }

    fun publishMessage(topic: String, payload: String) {
        try {
            mqttClient?.publish(topic, MqttMessage(payload.toByteArray()))
        } catch (e: Exception) {
            Log.e(TAG, "Publish failed: ${e.message}")
        }
    }

    // WebRTC Mesh Relay
    private fun createWebRtcRelay(peerId: String) {
        if (webRtcPeers.containsKey(peerId)) return
        if (webRtcPeers.size >= MAX_MESH_NODES) return
        val relay = WebRtcRelay(peerId,
            onSignal = { signal ->
                publishMessage(MESH_TOPIC_SIGNAL, signal)
            }
        )
        webRtcPeers[peerId] = relay
        relay.start()
        Log.d(TAG, "WebRTC relay started for: $peerId")
    }

    private fun handleWebRtcSignal(payload: String) {
        // Format: peerId|sdp|type
        try {
            val parts = payload.split("|")
            if (parts.size >= 3) {
                val peerId = parts[0]
                val relay = webRtcPeers[peerId] ?: return
                relay.onRemoteSignal(parts[1], parts[2])
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebRTC signal error: ${e.message}")
        }
    }

    private fun closeAllWebRtc() {
        for ((_, relay) in webRtcPeers) {
            relay.close()
        }
        webRtcPeers.clear()
    }

    fun stopMesh() {
        isMeshRunning = false
        meshJob?.cancel()
        try {
            mqttClient?.disconnect()
            mqttClient?.close()
        } catch (e: Exception) {
            Log.e(TAG, "MQTT close error: ${e.message}")
        }
        closeAllWebRtc()
        peers.clear()
        role = Role.SINGLE_NODE
    }

    // Simple WebRTC Relay placeholder
    // Replace with real org.webrtc.PeerConnection in production
    inner class WebRtcRelay(
        private val peerId: String,
        private val onSignal: (String) -> Unit
    ) {
        private var isActive = false

        fun start() {
            isActive = true
            // In real APK: create PeerConnection, add video track, create offer
            // Here: send fake signal via MQTT to establish mesh relay
            onSignal.invoke("$peerId|offer|mesh-relay-$nodeId")
            Log.d(TAG, "WebRTC relay active for $peerId")
        }

        fun onRemoteSignal(sdp: String, type: String) {
            if (!isActive) return
            Log.d(TAG, "WebRTC signal from $peerId type: $type")
            onMessage?.invoke(MESH_TOPIC_SIGNAL, "$peerId:$type:$sdp")
        }

        fun close() {
            isActive = false
            Log.d(TAG, "WebRTC closed for $peerId")
        }
    }
}
