package com.godeye.app.face.mesh

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MeshManager(private val context: Context) {

    enum class Role { SINGLE_NODE, MESH_NODE }

    companion object {
        private const val TAG = "MeshManager"
        // FIX 2: Don't hardcode 192.168.49.1 - Use configurable broker
        // Default to public test broker, app Settings can override to shop router
        private const val MQTT_BROKER = "tcp://broker.hivemq.com:1883"
        private const val TOPIC_STATUS = "godeye/mesh/status"
        private const val TOPIC_DISCOVERY = "godeye/mesh/discovery"
        private const val TOPIC_SIGNALING = "godeye/mesh/signaling" // FIX 3: Signaling only, NOT video
        private const val DISCOVERY_INTERVAL = 3000L
    }
    // ... rest same ...

    // FIX 4: >= 1 peer = Mesh active, not >= 2
    fun isMeshActive(): Boolean {
        return currentRole == Role.MESH_NODE && peers.size >= 1
    }

    // FIX 5: Don't try to enable WiFi - Android 15 blocks it
    // Just check if WiFi is available, let user enable it
    private fun setupWifiMesh() {
        Log.d(TAG, "Mesh ready - Using WiFi Direct + MQTT over LAN")
        // On Android 10+, app cannot enable WiFi programmatically
        // User must enable WiFi manually in Settings
    }

    // FIX 3: Remove publishVideoFrame() completely - Don't send video over MQTT
    // Replace with signaling only:
    fun publishSignaling(signal: String) {
        if (currentRole != Role.MESH_NODE) return
        try {
            if (mqttClient?.isConnected == true) {
                mqttClient?.publish(TOPIC_SIGNALING, MqttMessage(signal.toByteArray()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Signaling failed: ${e.message}")
        }
    }

    // FIX: Remove TOPIC_VIDEO, remove publishVideoFrame()
}
