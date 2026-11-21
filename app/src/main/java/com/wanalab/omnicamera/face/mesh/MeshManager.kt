package com.wanalab.omnicamera.mesh

import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.webrtc.*
import java.util.concurrent.ConcurrentHashMap

data class MeshNode(
    val cameraId: String,
    val ip: String,
    val hops: Int,
    val signal: Int, // -100 to -30, higher is better
    val battery: Int,
    val isOnline: Boolean,
    val isRelay: Boolean,
    val lastSeen: Long = System.currentTimeMillis()
)

data class MeshVideoRoute(
    val targetCameraId: String,
    val relayVia: String?, // null = direct
    val hops: Int,
    val latencyMs: Long
)

data class FaceMeshEvent(
    val cameraId: String,
    val personName: String?,
    val confidence: Float,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float
)

class MeshManager(private val context: Context) {

    private val TAG = "MeshManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    // Nodes map
    private val nodes = ConcurrentHashMap<String, MeshNode>()

    // Flows to UI
    private val _nodesFlow = MutableSharedFlow<List<MeshNode>>(replay = 1)
    val nodesFlow: SharedFlow<List<MeshNode>> = _nodesFlow

    private val _routeFlow = MutableSharedFlow<MeshVideoRoute>(extraBufferCapacity = 20)
    val routeFlow: SharedFlow<MeshVideoRoute> = _routeFlow

    private val _faceMeshFlow = MutableSharedFlow<FaceMeshEvent>(extraBufferCapacity = 50)
    val faceMeshFlow: SharedFlow<FaceMeshEvent> = _faceMeshFlow

    // MQTT
    private var mqttClient: MqttClient? = null
    private val brokerUrl = "tcp://127.0.0.1:1883" // local broker on phone, offline

    // WebRTC
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val videoRelays = ConcurrentHashMap<String, String>() // target -> relayVia

    fun start() {
        startLocalMqttBroker()
        startMqttClient()
        startWifiDirectDiscovery()
        startWebRtcFactory()
        startHealthCheck()
    }

    // 1. LOCAL MQTT BROKER - Phone is hub, offline, no internet
    private fun startLocalMqttBroker() {
        scope.launch {
            try {
                // Moquette embedded broker on phone
                val config = io.moquette.broker.config.MemoryConfig()
                config.setProperty("port", "1883")
                config.setProperty("host", "0.0.0.0")
                val server = io.moquette.broker.Server()
                server.startServer(config)
                Log.d(TAG, "Local MQTT broker started on 1883, offline")
            } catch (e: Exception) {
                Log.e(TAG, "Broker start failed: ${e.message}")
            }
        }
    }

    // 2. MQTT CLIENT - Cameras publish to phone
    private fun startMqttClient() {
        scope.launch {
            try {
                mqttClient = MqttClient(brokerUrl, "wanalab_hub_${System.currentTimeMillis()}", MemoryPersistence())
                val options = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                    connectionTimeout = 5
                }
                mqttClient?.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        Log.d(TAG, "MQTT lost, auto reconnect")
                    }
                    override fun messageArrived(topic: String, message: MqttMessage) {
                        handleMqttMessage(topic, String(message.payload))
                    }
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })
                mqttClient?.connect(options)
                // Subscribe to all camera topics
                mqttClient?.subscribe("wanalab/cam/+/status")
                mqttClient?.subscribe("wanalab/cam/+/face")
                mqttClient?.subscribe("wanalab/cam/+/mesh")
                mqttClient?.subscribe("wanalab/cam/+/relay_request")
                Log.d(TAG, "MQTT client connected, offline")
            } catch (e: Exception) {
                Log.e(TAG, "MQTT connect failed: ${e.message}")
            }
        }
    }

    private fun handleMqttMessage(topic: String, payload: String) {
        scope.launch {
            try {
                when {
                    topic.endsWith("/status") -> {
                        val node = gson.fromJson(payload, MeshNode::class.java)
                        nodes[node.cameraId] = node.copy(lastSeen = System.currentTimeMillis(), isOnline = true)
                        emitNodes()
                        checkFailover(node.cameraId)
                    }
                    topic.endsWith("/face") -> {
                        val event = gson.fromJson(payload, FaceMeshEvent::class.java)
                        _faceMeshFlow.emit(event)
                        // Forward face event to all other cameras so they track same person
                        publishToAll("wanalab/face/track", payload)
                    }
                    topic.endsWith("/mesh") -> {
                        val node = gson.fromJson(payload, MeshNode::class.java)
                        nodes[node.cameraId] = node
                        emitNodes()
                    }
                    topic.endsWith("/relay_request") -> {
                        // Camera asks for relay, find best relay
                        val targetId = payload.trim()
                        val relay = findBestRelay(targetId)
                        if (relay != null) {
                            publish("wanalab/cam/$targetId/relay_assign", relay.cameraId)
                            _routeFlow.emit(MeshVideoRoute(targetId, relay.cameraId, relay.hops + 1, 0))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "MQTT handle error: ${e.message}")
            }
        }
    }

    fun publish(topic: String, payload: String) {
        try {
            mqttClient?.publish(topic, MqttMessage(payload.toByteArray()).apply { qos = 1 })
        } catch (e: Exception) {
            Log.e(TAG, "Publish failed: ${e.message}")
        }
    }

    fun publishToAll(topic: String, payload: String) {
        publish(topic, payload)
    }

    fun publishFaceEvent(event: FaceMeshEvent) {
        publish("wanalab/cam/${event.cameraId}/face", gson.toJson(event))
    }

    // 3. WIFI MESH 802.11s - Discovery via WiFi Direct + ESP32 Mesh
    private fun startWifiDirectDiscovery() {
        // Android WiFi Direct for phone to camera discovery
        // ESP32-CAMs run 802.11s mesh in firmware, phone sees them as one mesh
        val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        val channel = manager?.initialize(context, context.mainLooper, null)
        scope.launch {
            while (isActive) {
                try {
                    manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() { Log.d(TAG, "WiFi Direct discovery started") }
                        override fun onFailure(reason: Int) { Log.d(TAG, "Discovery failed $reason") }
                    })
                } catch (e: Exception) { }
                delay(10000) // rediscover every 10 sec
            }
        }
        // ESP32 mesh announce is via MQTT, already handled above
    }

    // For ESP32 firmware - send this command to ESP32 to join mesh
    fun getEsp32MeshCommand(meshId: String, password: String, channel: Int = 6): String {
        // Flash this in ESP32 Arduino code: painlessMesh library
        return """
        // ESP32-CAM Mesh Code - Arduino, painlessMesh
        #include <painlessMesh.h>
        painlessMesh mesh;
        void setup() {
            mesh.init("$meshId", "$password", $channel, WIFI_AP_STA, 6);
            mesh.onReceive([](uint32_t from, String &msg) {
                // Forward video chunk to next hop
            });
        }
        void loop() { mesh.update(); }
        """.trimIndent()
    }

    // 4. WEBRTC MESH RELAY - Video hops if direct fails
    private fun startWebRtcFactory() {
        try {
            val options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(EglBase.create().eglBaseContext))
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(EglBase.create().eglBaseContext, true, true))
                .createPeerConnectionFactory()
        } catch (e: Exception) {
            Log.e(TAG, "WebRTC init failed: ${e.message}")
        }
    }

    fun createRelayConnection(targetCameraId: String, relayVia: String) {
        scope.launch {
            try {
                val factory = peerConnectionFactory ?: return@launch
                val iceServers = listOf(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
                    // No TURN, LAN only, offline. For LAN, STUN not even needed.
                )
                val config = PeerConnection.RTCConfiguration(iceServers)
                val pc = factory.createPeerConnection(config, object : PeerConnection.Observer {
                    override fun onIceCandidate(candidate: IceCandidate) {
                        // Send candidate via MQTT to relay camera
                        publish("wanalab/cam/$relayVia/candidate", gson.toJson(candidate))
                    }
                    override fun onAddStream(stream: MediaStream) {
                        // Video arrived via relay, show in UI
                        Log.d(TAG, "Relay video from $targetCameraId via $relayVia")
                    }
                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                        if (state == PeerConnection.IceConnectionState.FAILED) {
                            // Try next relay
                            findBestRelay(targetCameraId)?.let {
                                createRelayConnection(targetCameraId, it.cameraId)
                            }
                        }
                    }
                    override fun onSignalingChange(state: PeerConnection.SignalingState) {}
                    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
                    override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
                    override fun onRemoveStream(stream: MediaStream) {}
                    override fun onDataChannel(channel: DataChannel) {}
                    override fun onRenegotiationNeeded() {}
                })
                if (pc != null) {
                    peerConnections[targetCameraId] = pc
                    videoRelays[targetCameraId] = relayVia
                    // Create offer, send via MQTT to relay camera
                    pc.createOffer(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription) {
                            pc.setLocalDescription(this, desc)
                            publish("wanalab/cam/$relayVia/offer", gson.toJson(desc))
                        }
                        override fun onSetSuccess() {}
                        override fun onCreateFailure(error: String) {}
                        override fun onSetFailure(error: String) {}
                    }, MediaConstraints())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Relay create failed: ${e.message}")
            }
        }
    }

    // 5. FAILOVER LOGIC - Core of mesh
    private fun checkFailover(cameraId: String) {
        val node = nodes[cameraId] ?: return
        // If signal very weak, find relay before it drops
        if (node.signal < -85 && node.isOnline) {
            val relay = findBestRelay(cameraId)
            if (relay != null && relay.cameraId != cameraId) {
                Log.d(TAG, "Failover: $cameraId weak ${node.signal}, relay via ${relay.cameraId}")
                createRelayConnection(cameraId, relay.cameraId)
                scope.launch {
                    _routeFlow.emit(MeshVideoRoute(cameraId, relay.cameraId, relay.hops + 1, 0))
                }
            }
        }
    }

    fun findBestRelay(targetId: String): MeshNode? {
        // Best = online, not target, lowest hops, best signal, highest battery
        return nodes.values
            .filter { it.cameraId != targetId && it.isOnline && it.isRelay }
            .minByOrNull { it.hops * 20 - it.signal - (it.battery / 10) }
    }

    fun getRouteForCamera(targetId: String): MeshVideoRoute {
        val direct = nodes[targetId]
        if (direct != null && direct.isOnline && direct.signal > -75) {
            return MeshVideoRoute(targetId, null, 0, 0) // direct, no relay
        }
        val relay = findBestRelay(targetId)
        return if (relay != null) {
            MeshVideoRoute(targetId, relay.cameraId, relay.hops + 1, 0)
        } else {
            MeshVideoRoute(targetId, null, 0, -1) // no route, offline
        }
    }

    // 6. HEALTH CHECK - Mark offline if no heartbeat in 15 sec
    private fun startHealthCheck() {
        scope.launch {
            while (isActive) {
                delay(5000)
                val now = System.currentTimeMillis()
                var changed = false
                for ((id, node) in nodes) {
                    if (now - node.lastSeen > 15000 && node.isOnline) {
                        nodes[id] = node.copy(isOnline = false)
                        changed = true
                        // Auto trigger failover
                        val relay = findBestRelay(id)
                        if (relay != null) {
                            createRelayConnection(id, relay.cameraId)
                        }
                    }
                }
                if (changed) emitNodes()
            }
        }
    }

    private suspend fun emitNodes() {
        _nodesFlow.emit(nodes.values.sortedBy { it.hops }.toList())
    }

    fun stop() {
        scope.cancel()
        try { mqttClient?.disconnect() } catch (e: Exception) {}
        peerConnections.values.forEach { it.close() }
    }
}
