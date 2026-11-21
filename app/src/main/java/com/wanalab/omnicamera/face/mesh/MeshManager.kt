package com.wanalab.omnicamera.mesh

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

data class MeshNode(
    val cameraId: String,
    val ip: String,
    val name: String,
    val hops: Int = 1,
    val signalDbm: Int = -50,
    val battery: Int = 100,
    val isRelay: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

data class MeshRelayPath(
    val targetId: String,
    val relayId: String,
    val relayIp: String,
    val hops: Int
)

class MeshManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _nodes = MutableStateFlow<List<MeshNode>>(emptyList())
    val nodes: StateFlow<List<MeshNode>> = _nodes

    private val _activeRelay = MutableStateFlow<MeshRelayPath?>(null)
    val activeRelay: StateFlow<MeshRelayPath?> = _activeRelay

    private var mqttClient: MqttClient? = null
    private var isBrokerRunning = false

    private val nodeMap = mutableMapOf<String, MeshNode>()

    // Start all mesh layers
    fun startMesh() {
        startLocalMqttBroker()
        startMqttClient()
        startHeartbeat()
        startFailoverWatcher()
    }

    // 1. MQTT LOCAL BROKER - Phone becomes hub, offline
    // We use embedded broker via Moquette in real app, here we use local TCP broker on 1883
    // For simple version, we connect to local broker running on phone or on one ESP32
    private fun startLocalMqttBroker() {
        // In production, use Moquette or HiveMQ embedded
        // For now, we start a lightweight broker on 1883 using Paho server stub
        // If broker already on ESP32 mesh root, skip this
        isBrokerRunning = true
    }

    private fun startMqttClient() {
        scope.launch {
            try {
                val clientId = "wanalab-phone-${System.currentTimeMillis()}"
                mqttClient = MqttClient("tcp://127.0.0.1:1883", clientId, MemoryPersistence())
                val opts = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                    connectionTimeout = 5
                }
                mqttClient?.connect(opts)
                // Listen to all mesh topics
                mqttClient?.subscribe("wanalab/mesh/+/heartbeat", 0)
                mqttClient?.subscribe("wanalab/mesh/+/face", 0)
                mqttClient?.subscribe("wanalab/mesh/+/status", 0)
                mqttClient?.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {}
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        if (topic == null || message == null) return
                        onMeshMessage(topic, String(message.payload))
                    }
                })
                // Announce phone as hub
                publish("wanalab/mesh/phone/status", JSONObject().apply {
                    put("type", "hub")
                    put("isRelay", true)
                }.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun publish(topic: String, payload: String) {
        try {
            mqttClient?.publish(topic, MqttMessage(payload.toByteArray()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onMeshMessage(topic: String, payload: String) {
        try {
            val json = JSONObject(payload)
            val parts = topic.split("/")
            if (parts.size < 3) return
            val cameraId = parts[2]
            if (topic.contains("heartbeat")) {
                val node = MeshNode(
                    cameraId = cameraId,
                    ip = json.optString("ip", ""),
                    name = json.optString("name", cameraId),
                    hops = json.optInt("hops", 1),
                    signalDbm = json.optInt("signal", -60),
                    battery = json.optInt("battery", 100),
                    isRelay = json.optBoolean("isRelay", false),
                    lastSeen = System.currentTimeMillis()
                )
                nodeMap[cameraId] = node
                _nodes.value = nodeMap.values.toList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 2. HEARTBEAT - Every camera sends every 3 seconds
    private fun startHeartbeat() {
        scope.launch {
            while (isActive) {
                publish("wanalab/mesh/phone/heartbeat", JSONObject().apply {
                    put("ip", getPhoneIp())
                    put("name", "Phone Hub")
                    put("hops", 0)
                    put("signal", -30)
                    put("isRelay", true)
                }.toString())
                delay(3000)
            }
        }
    }

    // 3. FAILOVER WATCHER - If node dead 10 sec, find relay
    private fun startFailoverWatcher() {
        scope.launch {
            while (isActive) {
                delay(5000)
                val now = System.currentTimeMillis()
                val dead = nodeMap.values.filter { now - it.lastSeen > 10000 }
                for (deadNode in dead) {
                    // Try to find relay path
                    val relay = findBestRelay(deadNode.cameraId)
                    if (relay != null) {
                        _activeRelay.value = relay
                        // Tell WebRTC to switch to relay
                        WebRtcMeshRelay.switchToRelay(relay)
                    }
                }
            }
        }
    }

    // Pick best relay: lowest hops, best signal, high battery
    fun findBestRelay(targetId: String): MeshRelayPath? {
        val now = System.currentTimeMillis()
        val candidates = nodeMap.values.filter {
            it.cameraId != targetId && now - it.lastSeen < 10000 && it.isRelay
        }.sortedWith(compareBy<MeshNode> { it.hops }.thenByDescending { it.signalDbm }.thenByDescending { it.battery })
        val best = candidates.firstOrNull() ?: return null
        return MeshRelayPath(
            targetId = targetId,
            relayId = best.cameraId,
            relayIp = best.ip,
            hops = best.hops + 1
        )
    }

    // 4. WIFI MESH 802.11s - For ESP32-CAM nodes
    // ESP32 runs painlessMesh or ESP-WIFI-MESH, phone talks to root node
    fun getMeshRootIp(): String {
        // Root node is usually 192.168.4.1 or mesh gateway
        // It forwards to all mesh nodes
        return "192.168.4.1"
    }

    fun getCameraUrlViaMesh(cameraId: String): String {
        // If direct fails, use relay: http://root/mesh/cameraId/stream
        val relay = _activeRelay.value
        return if (relay != null && relay.targetId == cameraId) {
            "http://${relay.relayIp}:81/relay?target=$cameraId"
        } else {
            "http://${getMeshRootIp()}/mesh/$cameraId/stream"
        }
    }

    private fun getPhoneIp(): String {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wm.connectionInfo.ipAddress
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }

    fun stop() {
        scope.cancel()
        try { mqttClient?.disconnect() } catch (e: Exception) {}
    }
}

// 5. WEBRTC MESH RELAY - Video relay when RTSP direct fails
object WebRtcMeshRelay {
    private var currentRelay: MeshRelayPath? = null

    fun switchToRelay(relay: MeshRelayPath) {
        currentRelay = relay
        // In real code: close old PeerConnection, open new to relayIp
        // Relay forwards RTP packets from target camera
        println("Switching ${relay.targetId} via relay ${relay.relayId} at ${relay.relayIp}")
    }

    fun getCurrentRelay(): MeshRelayPath? = currentRelay

    fun isRelaying(): Boolean = currentRelay != null
}

// 6. ESP32 MESH NODE - Example Arduino code for ESP32-CAM to join mesh
// Paste this logic in ESP32 firmware, not Android
/*
#include <painlessMesh.h>
painlessMesh mesh;
void setup() {
  mesh.init("wanalab-mesh", "password123", 5555);
  mesh.onReceive([](uint32_t from, String &msg) {
    // Forward face event, video request
  });
}
void loop() {
  mesh.update();
  // Send heartbeat every 3 sec
  // {"ip":"192.168.4.5","name":"Cam-01","hops":1,"signal":-55,"isRelay":true}
}
*/
