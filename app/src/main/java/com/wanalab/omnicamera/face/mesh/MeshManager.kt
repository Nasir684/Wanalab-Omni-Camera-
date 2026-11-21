package com.meshcam.mesh

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.webrtc.*

/**
 * MeshManager.kt - APK safe
 * Works with 1 camera (Solo) or up to 3 cameras (Mesh)
 * Stack: WiFi Direct / Hotspot Discovery + Local MQTT Broker Client + WebRTC Mesh Relay
 */
class MeshManager(
    private val context: Context,
    private val deviceId: String = "CAM_${System.currentTimeMillis() % 10000}",
    private val onPeerVideoTrack: (String, VideoTrack) -> Unit = { _, _ -> },
    private val onMessage: (String, String) -> Unit = { _, _ -> }
) {
    companion object {
        private const val TAG = "MeshManager"
        private const val MQTT_BROKER_LOCAL = "tcp://192.168.49.1:1883"
        private const val MQTT_BROKER_FALLBACK = "tcp://127.0.0.1:1883"
        private const val TOPIC_DISCOVERY = "mesh/discovery"
        private const val TOPIC_SIGNAL = "mesh/signal/"
        private const val MAX_CAMERAS = 3
    }

    enum class Mode {
        SOLO, MESH
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mqttClient: MqttClient? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var eglBase: EglBase? = null

    private val _mode = MutableStateFlow(Mode.SOLO)
    val mode: StateFlow<Mode> = _mode

    private val _connectedCameras = MutableStateFlow<List<String>>(listOf(deviceId))
    val connectedCameras: StateFlow<List<String>> = _connectedCameras

    private val peerConnections = mutableMapOf<String, PeerConnection>()
    private val peerVideoSources = mutableMapOf<String, VideoSource>()

    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var isRunning = false

    fun start(localStream: MediaStream? = null) {
        if (isRunning) return
        isRunning = true
        Log.d(TAG, "Starting MeshManager as $deviceId Solo mode")

        initWebRTC()
        if (localStream != null) {
            attachLocalStream(localStream)
        }
        scope.launch {
            connectMqttWithFallback()
            startDiscoveryLoop()
            monitorCameraCount()
        }
    }

    private fun initWebRTC() {
        eglBase = EglBase.create()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase?.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase?.eglBaseContext, true, true))
            .setOptions(options)
            .createPeerConnectionFactory()
    }

    private fun attachLocalStream(stream: MediaStream) {
        if (stream.videoTracks.isNotEmpty()) {
            localVideoTrack = stream.videoTracks[0]
        }
        if (stream.audioTracks.isNotEmpty()) {
            localAudioTrack = stream.audioTracks[0]
        }
    }

    fun setLocalVideoTrack(track: VideoTrack?) {
        localVideoTrack = track
        // Push to existing peers if in mesh mode
        peerConnections.forEach { (peerId, pc) ->
            track?.let {
                pc.addTrack(it, listOf("mesh-stream-$deviceId"))
                createOffer(peerId)
            }
        }
    }

    private suspend fun connectMqttWithFallback() {
        val brokers = listOf(MQTT_BROKER_LOCAL, MQTT_BROKER_FALLBACK)
        for (broker in brokers) {
            try {
                val client = MqttClient(broker, deviceId, MemoryPersistence())
                val opts = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                    connectionTimeout = 3
                }
                client.connect(opts)
                client.subscribe(TOPIC_DISCOVERY, 0)
                client.subscribe(TOPIC_SIGNAL + deviceId, 0)
                client.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        Log.d(TAG, "MQTT connected: $serverURI")
                        publishDiscovery()
                    }
                    override fun connectionLost(cause: Throwable?) {
                        Log.w(TAG, "MQTT lost: ${cause?.message}")
                    }
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        if (topic == TOPIC_DISCOVERY) handleDiscovery(message.toString())
                        if (topic?.startsWith("mesh/signal/") == true) handleSignal(message.toString())
                    }
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })
                mqttClient = client
                Log.d(TAG, "MQTT connected to $broker")
                return
            } catch (e: Exception) {
                Log.w(TAG, "MQTT failed $broker: ${e.message}, trying next, Solo mode still works")
            }
        }
        // Even if MQTT fails, Solo mode works
        _mode.value = Mode.SOLO
        _connectedCameras.value = listOf(deviceId)
    }

    private fun publishDiscovery() {
        val payload = "{\"id\":\"$deviceId\",\"mode\":\"${_mode.value}\",\"ts\":${System.currentTimeMillis()}}"
        try {
            mqttClient?.publish(TOPIC_DISCOVERY, MqttMessage(payload.toByteArray()))
        } catch (e: Exception) {
            Log.w(TAG, "Discovery publish failed, Solo mode: ${e.message}")
        }
    }

    private fun handleDiscovery(json: String) {
        if (!json.contains("\"id\"")) return
        val id = json.substringAfter("\"id\":\"").substringBefore("\"")
        if (id == deviceId || id.isBlank()) return
        scope.launch {
            val current = _connectedCameras.value.toMutableList()
            if (!current.contains(id) && current.size < MAX_CAMERAS) {
                current.add(id)
                _connectedCameras.value = current
                Log.d(TAG, "Peer found: $id Total: ${current.size}")
                if (current.size > 1) {
                    _mode.value = Mode.MESH
                    createPeerConnection(id)
                }
            }
        }
    }

    private fun startDiscoveryLoop() {
        scope.launch {
            while (isRunning) {
                publishDiscovery()
                delay(3000)
            }
        }
    }

    private suspend fun monitorCameraCount() {
        // If peers disappear, fallback to SOLO automatically
        while (isRunning) {
            delay(10000)
            // In real APK, this is updated by MQTT timeout logic
            // For now, if only self remains, ensure SOLO
            if (_connectedCameras.value.size <= 1 && _mode.value != Mode.SOLO) {
                _mode.value = Mode.SOLO
                Log.d(TAG, "Fallback to SOLO - only 1 camera")
            }
        }
    }

    private fun createPeerConnection(peerId: String) {
        val factory = peerConnectionFactory ?: return
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val pc = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { sendSignal(peerId, "candidate|${it.sdpMid}|${it.sdpMLineIndex}|${it.sdp}") }
            }
            override fun onAddStream(stream: MediaStream?) {}
            override fun onTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                streams?.firstOrNull()?.videoTracks?.firstOrNull()?.let { track ->
                    onPeerVideoTrack(peerId, track)
                }
            }
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        }) ?: return

        localVideoTrack?.let { pc.addTrack(it, listOf("mesh-stream-$deviceId")) }
        localAudioTrack?.let { pc.addTrack(it, listOf("mesh-stream-$deviceId")) }

        peerConnections[peerId] = pc
        createOffer(peerId)
    }

    private fun createOffer(peerId: String) {
        val pc = peerConnections[peerId] ?: return
        val constraints = MediaConstraints()
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            sendSignal(peerId, "offer|${it.description}")
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(msg: String?) { Log.e(TAG, "Offer fail $msg") }
            override fun onSetFailure(msg: String?) {}
        }, constraints)
    }

    private fun handleSignal(json: String) {
        // Format: from|type|payload
        val parts = json.split("|", limit = 4)
        if (parts.size < 3) return
        val fromId = parts[0]
        val type = parts[1]
        val payload = parts.drop(2).joinToString("|")
        if (fromId == deviceId) return

        var pc = peerConnections[fromId]
        if (pc == null && (type == "offer")) {
            createPeerConnection(fromId)
            pc = peerConnections[fromId]
        }
        when (type) {
            "offer" -> {
                pc?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() { createAnswer(fromId) }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, SessionDescription(SessionDescription.Type.OFFER, payload))
            }
            "answer" -> {
                pc?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, SessionDescription(SessionDescription.Type.ANSWER, payload))
            }
            "candidate" -> {
                val cParts = payload.split("|")
                if (cParts.size >= 3) {
                    val candidate = IceCandidate(cParts[0], cParts[1].toIntOrNull() ?: 0, cParts[2])
                    pc?.addIceCandidate(candidate)
                }
            }
        }
    }

    private fun createAnswer(peerId: String) {
        val pc = peerConnections[peerId] ?: return
        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() { sendSignal(peerId, "answer|${it.description}") }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(msg: String?) { Log.e(TAG, "Answer fail $msg") }
            override fun onSetFailure(msg: String?) {}
        }, MediaConstraints())
    }

    private fun sendSignal(toId: String, payload: String) {
        val full = "$deviceId|$payload"
        try {
            mqttClient?.publish("mesh/signal/$toId", MqttMessage(full.toByteArray()))
        } catch (e: Exception) {
            Log.w(TAG, "Signal failed: ${e.message}")
        }
    }

    fun broadcastMessage(message: String) {
        try {
            mqttClient?.publish("mesh/chat", MqttMessage(message.toByteArray()))
        } catch (e: Exception) {
            // Solo mode: deliver locally
            onMessage(deviceId, message)
        }
    }

    fun getCameraCount(): Int {
        return _connectedCameras.value.size
    }

    fun isSolo(): Boolean {
        return _mode.value == Mode.SOLO
    }

    fun stop() {
        isRunning = false
        scope.cancel()
        peerConnections.values.forEach { it.close() }
        peerConnections.clear()
        try { mqttClient?.disconnect() } catch (e: Exception) {}
        try { peerConnectionFactory?.dispose() } catch (e: Exception) {}
        eglBase?.release()
    }
}
