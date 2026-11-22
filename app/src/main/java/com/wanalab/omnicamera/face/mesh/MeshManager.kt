package com.wanalab.godeye

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MeshManager(private val context: Context) {

    enum class Role { SINGLE_NODE, MESH_NODE }

    interface Listener {
        fun onPeerFound(peerId: String, peerCount: Int)
        fun onPeerLost(peerId: String, peerCount: Int)
        fun onSignalingReceived(fromPeerId: String, signal: String)
        fun onMeshActiveChanged(active: Boolean)
    }

    companion object {
        private const val TAG = "MeshManager"
        // FIXED: No hardcoded 192.168.49.1 - configurable, default public test broker
        // Override in Settings to shop router: tcp://192.168.1.1:1883
        private const val DEFAULT_BROKER = "tcp://broker.hivemq.com:1883"
        private const val TOPIC_STATUS = "godeye/mesh/status"
        private const val TOPIC_DISCOVERY = "godeye/mesh/discovery"
        private const val TOPIC_SIGNALING = "godeye/mesh/signaling"
        private const val DISCOVERY_INTERVAL = 3000L
        private const val PEER_TIMEOUT_MS = 10000L
    }

    var currentRole: Role = Role.SINGLE_NODE
        private set

    var brokerUrl: String = DEFAULT_BROKER
        private set

    fun setBroker(url: String) {
        if (url.isNotBlank()) brokerUrl = url.trim()
    }

    private val myId: String = "node_${UUID.randomUUID().toString().take(8)}"
    private val peers = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var mqttClient: MqttClient? = null
    private var listener: Listener? = null
    private var isStarted = false
    private var lastMeshActive = false

    fun setListener(l: Listener?) {
        listener = l
    }

    fun start(role: Role = Role.MESH_NODE) {
        if (isStarted) return
        isStarted = true
        currentRole = role
        if (role != Role.MESH_NODE) return

        scope.launch {
            try {
                val client = MqttClient(brokerUrl, myId, MemoryPersistence())
                val opts = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                    connectionTimeout = 10
                    keepAliveInterval = 30
                }
                client.connect(opts)
                mqttClient = client

                client.subscribe(TOPIC_DISCOVERY, 0) { _, msg ->
                    handleDiscovery(String(msg.payload, Charsets.UTF_8))
                }
                client.subscribe(TOPIC_SIGNALING, 0) { _, msg ->
                    handleSignaling(String(msg.payload, Charsets.UTF_8))
                }
                client.subscribe(TOPIC_STATUS, 0) { _, _ -> }

                // Discovery loop - announce every 3 sec
                while (isStarted) {
                    try {
                        if (client.isConnected) {
                            val payload = "HELLO:$myId:${System.currentTimeMillis()}"
                            client.publish(TOPIC_DISCOVERY, MqttMessage(payload.toByteArray(Charsets.UTF_8)))
                        }
                    } catch (e: Exception) {}
                    // Prune dead peers
                    prunePeers()
                    checkMeshActiveChanged()
                    delay(DISCOVERY_INTERVAL)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Mesh start failed: ${e.message}")
            }
        }
    }

    private fun handleDiscovery(payload: String) {
        // Format: HELLO:<peerId>:<timestamp>
        val parts = payload.split(":")
        if (parts.size < 2) return
        val peerId = parts[1]
        if (peerId == myId) return
        val isNew = !peers.containsKey(peerId)
        peers[peerId] = System.currentTimeMillis()
        if (isNew) {
            listener?.onPeerFound(peerId, peers.size)
            checkMeshActiveChanged()
        }
    }

    private fun handleSignaling(payload: String) {
        // Format: <fromPeerId>|<signalJson>
        val idx = payload.indexOf("|")
        if (idx <= 0) return
        val from = payload.substring(0, idx)
        if (from == myId) return
        val signal = payload.substring(idx + 1)
        listener?.onSignalingReceived(from, signal)
    }

    private fun prunePeers() {
        val now = System.currentTimeMillis()
        val dead = peers.entries.filter { now - it.value > PEER_TIMEOUT_MS }.map { it.key }
        for (id in dead) {
            peers.remove(id)
            listener?.onPeerLost(id, peers.size)
        }
        if (dead.isNotEmpty()) checkMeshActiveChanged()
    }

    private fun checkMeshActiveChanged() {
        val active = isMeshActive()
        if (active != lastMeshActive) {
            lastMeshActive = active
            listener?.onMeshActiveChanged(active)
        }
    }

    // FIXED: >= 1 peer = Mesh active, not >= 2
    fun isMeshActive(): Boolean {
        return currentRole == Role.MESH_NODE && peers.size >= 1
    }

    fun getPeerCount(): Int = peers.size
    fun getPeerIds(): List<String> = peers.keys.toList()
    fun getMyId(): String = myId

    // FIXED: Don't try to enable WiFi - Android 10+ / 15 blocks it
    // Just log, user enables WiFi manually
    private fun setupWifiMesh() {
        Log.d(TAG, "Mesh ready - Using WiFi Direct + MQTT over LAN")
    }

    // FIXED: Signaling ONLY - never video over MQTT
    // Video goes via WebRTC / RTSP, MQTT is only for HELLO + SDP / ICE
    fun publishSignaling(signal: String) {
        if (currentRole != Role.MESH_NODE) return
        try {
            val client = mqttClient
            if (client?.isConnected == true) {
                val payload = "$myId|$signal"
                client.publish(TOPIC_SIGNALING, MqttMessage(payload.toByteArray(Charsets.UTF_8)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Signaling failed: ${e.message}")
        }
    }

    fun publishStatus(status: String) {
        if (currentRole != Role.MESH_NODE) return
        try {
            val client = mqttClient
            if (client?.isConnected == true) {
                val payload = "$myId|$status"
                client.publish(TOPIC_STATUS, MqttMessage(payload.toByteArray(Charsets.UTF_8)))
            }
        } catch (e: Exception) {}
    }

    fun stop() {
        isStarted = false
        try { scope.cancel() } catch (e: Exception) {}
        try {
            mqttClient?.let {
                if (it.isConnected) it.disconnect()
                it.close()
            }
        } catch (e: Exception) {}
        mqttClient = null
        peers.clear()
        lastMeshActive = false
    }
}
