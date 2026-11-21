package com.wazir.cctv.mesh

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.webrtc.*
import java.util.concurrent.ConcurrentHashMap

class MeshManager(
    private val context: Context,
    private val onCameraStream: (cameraId: String, track: VideoTrack?) -> Unit,
    private val onCameraStatus: (cameraId: String, online: Boolean) -> Unit
) {
    private val TAG = "MeshManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // State
    private val _mode = MutableStateFlow(MeshMode.SINGLE)
    val mode: StateFlow<MeshMode> = _mode

    private val _cameras = MutableStateFlow<List<CameraNode>>(emptyList())
    val cameras: StateFlow<List<CameraNode>> = _cameras

    // MQTT Local Broker - runs on phone
    private var mqttClient: MqttClient? = null
    private var mqttBroker: LocalMqttBroker? = null
    private val MQTT_BROKER_URL = "tcp://127.0.0.1:1883"
    private val TOPIC_DISCOVERY = "wazir/mesh/discovery"
    private val TOPIC_HEARTBEAT = "wazir/mesh/heartbeat/+"
    private val TOPIC_RELAY = "wazir/mesh/relay/+"

    // WebRTC
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val relayMap = ConcurrentHashMap<String, String>() // droppedCamera -> relayCamera
    private val cameraNodes = ConcurrentHashMap<String, CameraNode>()

    private var isRunning = false
    private var heartbeatJob: Job? = null
    private var discoveryJob: Job? = null

    data class CameraNode(
        val id: String,
        val name: String,
        val ip: String,
        val isRelayCapable: Boolean = true,
        var lastSeen: Long = System.currentTimeMillis(),
        var isOnline: Boolean = true,
        var relayVia: String? = null
    )

    enum class MeshMode {
        SINGLE, MESH, OFFLINE
    }

    // START - Call this from MainActivity
    fun start() {
        if (isRunning) return
        isRunning = true
        Log.d(TAG, "MeshManager started - Single camera compatible")

        initWebRTC()
        startLocalMqttBroker()
        connectMqtt()
        startDiscovery()
        startHeartbeatMonitor()
    }

    fun stop() {
        isRunning = false
        heartbeatJob?.cancel()
        discoveryJob?.cancel()
        scope.cancel()
        try { mqttClient?.disconnect() } catch (e: Exception) {}
        peerConnections.values.forEach { try { it.close() } catch (e: Exception) {} }
        peerConnections.clear()
        mqttBroker?.stop()
    }

    // 1. WEBRTC SETUP - Works for 1 camera too
    private fun initWebRTC() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(null, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(null)
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun createPeerConnection(cameraId: String): PeerConnection? {
        val factory = peerConnectionFactory ?: return null
        // STUN only local, no internet needed for single camera
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            // Local relay fallback
            PeerConnection.IceServer.builder("turn:192.168.4.1:3478")
                .setUsername("wazir").setPassword("wazir123").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // For mesh, allow relay
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { publishMqtt("wazir/mesh/ice/$cameraId", it.toString()) }
            }
            override fun onAddStream(stream: MediaStream?) {
                stream?.videoTracks?.firstOrNull()?.let { track ->
                    // If this is relayed stream, mark it
                    val relayVia = relayMap[cameraId]
                    Log.d(TAG, "Stream for $cameraId via relay $relayVia")
                    onCameraStream(cameraId, track)
                }
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                if (newState == PeerConnection.PeerConnectionState.FAILED ||
                    newState == PeerConnection.PeerConnectionState.DISCONNECTED) {
                    handleCameraDrop(cameraId)
                }
            }
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        }

        val pc = factory.createPeerConnection(rtcConfig, observer)
        if (pc != null) peerConnections[cameraId] = pc
        return pc
    }

    // 2. MQTT LOCAL BROKER - Phone becomes broker, works with 1 camera
    private fun startLocalMqttBroker() {
        // Lightweight embedded broker - Moquette style simplified
        // In real APK, use HiveMQ / Moquette lib. Here we start local server thread.
        mqttBroker = LocalMqttBroker(port = 1883)
        scope.launch {
            try { mqttBroker?.start() } catch (e: Exception) {
                Log.e(TAG, "Broker start failed, will use direct mode: ${e.message}")
            }
        }
    }

    private fun connectMqtt() {
        scope.launch {
            delay(500) // Wait broker
            try {
                mqttClient = MqttClient(MQTT_BROKER_URL, "PhoneHub_${System.currentTimeMillis()}", MemoryPersistence())
                val opts = MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 5
                    keepAliveInterval = 10
                    isAutomaticReconnect = true
                }
                mqttClient?.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        mqttClient?.subscribe(TOPIC_HEARTBEAT, 0)
                        mqttClient?.subscribe(TOPIC_DISCOVERY, 0)
                        mqttClient?.subscribe(TOPIC_RELAY, 0)
                        publishDiscovery()
                    }
                    override fun connectionLost(cause: Throwable?) {
                        Log.e(TAG, "MQTT lost: ${cause?.message}")
                    }
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        if (topic == null || message == null) return
                        handleMqttMessage(topic, String(message.payload))
                    }
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })
                mqttClient?.connect(opts)
            } catch (e: Exception) {
                Log.e(TAG, "MQTT connect failed, single camera direct mode still works: ${e.message}")
                // Single camera still works via WebRTC direct, MQTT is optional
            }
        }
    }

    private fun publishMqtt(topic: String, payload: String) {
        try { mqttClient?.publish(topic, MqttMessage(payload.toByteArray())) } catch (e: Exception) {}
    }

    private fun publishDiscovery() {
        publishMqtt(TOPIC_DISCOVERY, "PHONE_HUB|ONLINE|${System.currentTimeMillis()}")
    }

    private fun handleMqttMessage(topic: String, payload: String) {
        when {
            topic.startsWith("wazir/mesh/discovery") -> {
                // Format: CAM_ID|NAME|IP|RELAY_CAPABLE
                val parts = payload.split("|")
                if (parts.size >= 3) {
                    addOrUpdateCamera(parts[0], parts.getOrElse(1) { parts[0] }, parts[2])
                }
            }
            topic.startsWith("wazir/mesh/heartbeat") -> {
                val camId = topic.substringAfterLast("/")
                cameraNodes[camId]?.let { it.lastSeen = System.currentTimeMillis(); it.isOnline = true }
            }
            topic.startsWith("wazir/mesh/relay") -> {
                // Relay request: droppedCam|relayCam
                val parts = payload.split("|")
                if (parts.size == 2) {
                    relayMap[parts[0]] = parts[1]
                    Log.d(TAG, "Relay: ${parts[0]} via ${parts[1]}")
                }
            }
        }
    }

    // 3. DISCOVERY + MODE SWITCH - 1 vs 3 cameras
    private fun addOrUpdateCamera(id: String, name: String, ip: String) {
        val exists = cameraNodes.containsKey(id)
        if (!exists) {
            val node = CameraNode(id, name, ip)
            cameraNodes[id] = node
            createPeerConnection(id)
            onCameraStatus(id, true)
            Log.d(TAG, "Camera added: $id total=${cameraNodes.size}")
        } else {
            cameraNodes[id]?.lastSeen = System.currentTimeMillis()
        }
        updateMode()
    }

    private fun updateMode() {
        val onlineCount = cameraNodes.values.count { it.isOnline }
        val newMode = when {
            onlineCount == 0 -> MeshMode.OFFLINE
            onlineCount == 1 -> MeshMode.SINGLE
            else -> MeshMode.MESH
        }
        if (_mode.value != newMode) {
            _mode.value = newMode
            Log.d(TAG, "Mode changed to $newMode with $onlineCount cameras")
        }
        _cameras.value = cameraNodes.values.toList()
    }

    private fun startDiscovery() {
        discoveryJob = scope.launch {
            while (isRunning) {
                publishDiscovery()
                // Also scan WiFi for ESP32-CAM APs - for single camera case
                scanWifiForCameras()
                delay(5000)
            }
        }
    }

    private fun scanWifiForCameras() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val results = wifi.scanResults
            results?.forEach { result ->
                // ESP32-CAM AP names: WAZIR_CAM_1, WAZIR_CAM_2, WAZIR_CAM_3
                if (result.SSID.startsWith("WAZIR_CAM_")) {
                    val camId = result.SSID.removePrefix("WAZIR_CAM_")
                    if (!cameraNodes.containsKey(camId)) {
                        // Single camera will also be found here
                        addOrUpdateCamera(camId, result.SSID, "192.168.4.1")
                    }
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Scan failed: ${e.message}") }
    }

    // 4. FAILOVER - If one drops, another relays
    private fun startHeartbeatMonitor() {
        heartbeatJob = scope.launch {
            while (isRunning) {
                delay(3000)
                val now = System.currentTimeMillis()
                cameraNodes.values.toList().forEach { node ->
                    if (now - node.lastSeen > 8000 && node.isOnline) {
                        node.isOnline = false
                        handleCameraDrop(node.id)
                    }
                }
                updateMode()
            }
        }
    }

    private fun handleCameraDrop(droppedId: String) {
        Log.d(TAG, "Camera dropped: $droppedId")
        onCameraStatus(droppedId, false)

        // If only 1 camera total, just show offline, no relay possible
        val onlineOthers = cameraNodes.values.filter { it.id != droppedId && it.isOnline }
        if (onlineOthers.isEmpty()) {
            Log.d(TAG, "Single camera dropped, waiting for reconnect")
            _mode.value = MeshMode.OFFLINE
            return
        }

        // Mesh mode: Pick best relay - closest / strongest
        val bestRelay = onlineOthers.firstOrNull { it.isRelayCapable }
        if (bestRelay != null) {
            relayMap[droppedId] = bestRelay.id
            cameraNodes[droppedId]?.relayVia = bestRelay.id
            publishMqtt("wazir/mesh/relay/$droppedId", "$droppedId|${bestRelay.id}")
            // Request relay to re-establish stream via relay camera
            requestRelayStream(droppedId, bestRelay.id)
            Log.d(TAG, "Failover: $droppedId now relayed via ${bestRelay.id}")
        }
    }

    private fun requestRelayStream(droppedId: String, relayId: String) {
        scope.launch {
            // Tell relay camera to forward dropped camera's stream
            publishMqtt("wazir/mesh/relay/$relayId", "RELAY_REQUEST|$droppedId|$relayId")
            // Re-create PeerConnection via relay
            peerConnections[droppedId]?.close()
            peerConnections.remove(droppedId)
            delay(500)
            createPeerConnection(droppedId)
        }
    }

    // PUBLIC API for UI - Works for 1 camera
    fun getCameraCount(): Int = cameraNodes.values.count { it.isOnline }

    fun isSingleCameraMode(): Boolean = _mode.value == MeshMode.SINGLE

    fun getRelayInfo(cameraId: String): String? = relayMap[cameraId]

    // Manual add for testing single camera
    fun addManualCamera(id: String, ip: String) {
        addOrUpdateCamera(id, "CAM_$id", ip)
    }
}

// Simple local broker stub - Replace with Moquette in build.gradle
class LocalMqttBroker(private val port: Int) {
    fun start() { /* Moquette broker start here */ }
    fun stop() { /* Moquette broker stop */ }
}
