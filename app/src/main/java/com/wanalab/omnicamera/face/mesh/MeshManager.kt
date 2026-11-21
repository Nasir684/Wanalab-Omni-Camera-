package com.wanalab.omnicamera.mesh

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

data class MeshNode(
    val cameraId: String,
    val ip: String,
    val name: String,
    val hops: Int, // 0 = direct to phone, 1 = via 1 relay, 2 = via 2 relays
    val signal: Int, // -30 strong, -80 weak
    val battery: Int = 100,
    val isRelay: Boolean = false,
    val isOnline: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis()
)

data class MeshVideoRoute(
    val targetCameraId: String,
    val relayCameraId: String?, // null = direct
    val route: List<String>, // example: [cam_01, cam_02, phone]
    val latencyMs: Long
)

class MeshManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _nodes = MutableStateFlow<List<MeshNode>>(emptyList())
    val nodes: StateFlow<List<MeshNode>> = _nodes

    private val _routes = MutableStateFlow<Map<String, MeshVideoRoute>>(emptyMap())
    val routes: StateFlow<Map<String, MeshVideoRoute>> = _routes

    private var mqttClient: MqttClient? = null
    private var isMeshRunning = false

    // Start mesh - call once when app opens, works offline
    fun startMesh() {
        if (isMeshRunning) return
        isMeshRunning = true

        // 1. Start local MQTT broker on phone - port 1883, no internet
        // Use Moquette embedded broker, or HiveMQ local
        LocalMqttBroker.start(port = 1883)

        // 2. Connect phone to local broker
        scope.launch { connectMqtt() }

        // 3. Start WiFi Mesh discovery - ESP32 802.11s + mDNS + SSDP
        WifiMeshScanner.start { foundNodes ->
            updateNodes(foundNodes)
            recalculateRoutes()
        }

        // 4. Start WebRTC Mesh Hub - phone as SFU relay
        WebRtcMeshHub.start()
    }

    fun stopMesh() {
        isMeshRunning = false
        LocalMqttBroker.stop()
        try { mqttClient?.disconnect() } catch (e: Exception) {}
        WifiMeshScanner.stop()
        WebRtcMeshHub.stop()
    }

    // MQTT - control plane for mesh
    private fun connectMqtt() {
        try {
            val clientId = "wanalab_phone_${System.currentTimeMillis()}"
            mqttClient = MqttClient("tcp://127.0.0.1:1883", clientId, MemoryPersistence())
            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
                connectionTimeout = 5
            }
            mqttClient?.connect(options)

            // Listen for all mesh topics
            mqttClient?.subscribe("wanalab/mesh/+/heartbeat") { topic, msg ->
                handleHeartbeat(topic, String(msg.payload))
            }
            mqttClient?.subscribe("wanalab/mesh/+/video/route") { topic, msg ->
                handleRouteUpdate(topic, String(msg.payload))
            }
            mqttClient?.subscribe("wanalab/face/detected") { _, msg ->
                // Face event from any mesh cam, forward to FaceEngine
                FaceMeshBridge.onFaceEvent(String(msg.payload))
            }
            mqttClient?.subscribe("wanalab/mesh/+/offline") { topic, _ ->
                val camId = topic.split("/")[2]
                markOffline(camId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Publish command to one camera or all cameras
    fun publish(topic: String, payload: String) {
        try {
            mqttClient?.publish(topic, MqttMessage(payload.toByteArray()).apply { qos = 1 })
        } catch (e: Exception) { }
    }

    fun sendPtz(cameraId: String, pan: Int, tilt: Int, zoom: Int) {
        publish("wanalab/mesh/$cameraId/ptz", "{\"pan\":$pan,\"tilt\":$tilt,\"zoom\":$zoom}")
    }

    fun wakeCamera(cameraId: String) {
        publish("wanalab/mesh/$cameraId/wake", "{\"wake\":true}")
    }

    // Node management
    private fun updateNodes(found: List<MeshNode>) {
        val merged = mutableMapOf<String, MeshNode>()
        for (old in _nodes.value) merged[old.cameraId] = old
        for (n in found) merged[n.cameraId] = n
        // Remove nodes not seen for 15 sec
        val now = System.currentTimeMillis()
        val alive = merged.values.filter { now - it.lastSeen < 15000 }
        _nodes.value = alive.sortedBy { it.hops }
    }

    private fun markOffline(cameraId: String) {
        _nodes.value = _nodes.value.map {
            if (it.cameraId == cameraId) it.copy(isOnline = false) else it
        }
        // Trigger failover - find relay for this camera
        scope.launch { failover(cameraId) }
    }

    // Core mesh logic - if camera drops, find relay
    suspend fun failover(downCameraId: String) {
        val bestRelay = findBestRelay(excludeId = downCameraId)
        if (bestRelay == null) {
            publish("wanalab/mesh/$downCameraId/status", "{\"status\":\"offline_no_relay\"}")
            return
        }
        // Ask best relay to forward down camera stream
        // Relay uses its ESP32 mesh link to down camera
        publish(
            "wanalab/mesh/${bestRelay.cameraId}/relay/start",
            "{\"target\":\"$downCameraId\",\"mode\":\"webrtc_relay\"}"
        )
        // Update route
        val newRoute = MeshVideoRoute(
            targetCameraId = downCameraId,
            relayCameraId = bestRelay.cameraId,
            route = listOf(downCameraId, bestRelay.cameraId, "phone"),
            latencyMs = 180
        )
        val map = _routes.value.toMutableMap()
        map[downCameraId] = newRoute
        _routes.value = map

        // Start WebRTC relay on phone side
        WebRtcMeshHub.startRelay(fromCamera = downCameraId, viaCamera = bestRelay.cameraId)
    }

    fun findBestRelay(excludeId: String): MeshNode? {
        return _nodes.value
            .filter { it.cameraId != excludeId && it.isOnline }
            .filter { it.signal > -75 } // only good signal relays
            .minByOrNull { it.hops * 20 - it.signal } // lowest hops, best signal
    }

    private fun recalculateRoutes() {
        val map = mutableMapOf<String, MeshVideoRoute>()
        for (node in _nodes.value) {
            if (node.hops == 0) {
                map[node.cameraId] = MeshVideoRoute(node.cameraId, null, listOf(node.cameraId, "phone"), 60)
            } else {
                // Via relay
                val relay = _nodes.value.firstOrNull { it.hops == 0 && it.isOnline }
                map[node.cameraId] = MeshVideoRoute(node.cameraId, relay?.cameraId, listOf(node.cameraId, relay?.cameraId ?: "mesh", "phone"), 150)
            }
        }
        _routes.value = map
    }

    // Get video URL - direct or relay
    fun getVideoUrlFor(cameraId: String): String {
        val route = _routes.value[cameraId]
        return if (route?.relayCameraId == null) {
            // Direct RTSP / MJPEG
            val node = _nodes.value.firstOrNull { it.cameraId == cameraId }
            "rtsp://${node?.ip}:554/stream"
        } else {
            // Via relay - WebRTC relay stream from phone hub
            "webrtc://relay/${cameraId}?via=${route.relayCameraId}"
        }
    }

    private fun handleHeartbeat(topic: String, payload: String) {
        // Example payload: {"cameraId":"cam_01","ip":"192.168.4.2","hops":1,"signal":-55,"battery":85}
        try {
            val camId = topic.split("/")[2]
            val ip = extractJson(payload, "ip")
            val hops = extractJson(payload, "hops").toIntOrNull() ?: 0
            val signal = extractJson(payload, "signal").toIntOrNull() ?: -60
            val battery = extractJson(payload, "battery").toIntOrNull() ?: 100
            val node = MeshNode(camId, ip, camId, hops, signal, battery, isRelay = hops > 0, isOnline = true)
            updateNodes(listOf(node))
        } catch (e: Exception) {}
    }

    private fun handleRouteUpdate(topic: String, payload: String) {
        recalculateRoutes()
    }

    private fun extractJson(json: String, key: String): String {
        val pattern = "\"$key\"\\s*:\\s*\"?([^\",}]+)\"?".toRegex()
        return pattern.find(json)?.groupValues?.get(1) ?: ""
    }
}

// Placeholders - plug real libs here
object LocalMqttBroker {
    fun start(port: Int = 1883) {
        // Use Moquette: io.moquette:moquette-broker:0.16
        // Broker runs on phone, no internet
    }
    fun stop() {}
}

object WifiMeshScanner {
    fun start(onFound: (List<MeshNode>) -> Unit) {
        // Scan mDNS _wanalab._tcp + SSDP + UDP broadcast 255.255.255.255:8888
        // ESP32 mesh sends heartbeat every 2 sec with hops and signal
    }
    fun stop() {}
}

object WebRtcMeshHub {
    fun start() {
        // Phone as SFU - receives WebRTC from each cam, forwards to viewers
        // Use LiveKit self hosted or Pion SFU on phone, offline
    }
    fun stop() {}
    fun startRelay(fromCamera: String, viaCamera: String) {
        // Create relay PeerConnection viaCamera -> phone -> viewer
    }
}

object FaceMeshBridge {
    fun onFaceEvent(payload: String) {}
}
