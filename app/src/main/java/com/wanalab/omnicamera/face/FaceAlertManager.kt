package com.wanalab.godeye

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// WanaLab God Eye v2.2 - FaceAlertManager.kt
// Unknown Face Alert - Vibrate + Snapshot + Local MQTT + Cloud
// Author: Nasir Khan - Dera Ismail Khan, Khyber Pakhtunkhwa - 2026-11-23
// PRO Rs. 2,500 / Year

object FaceAlertManager {

    const val VERSION = "2.2"
    const val APP_NAME = "WanaLab God Eye"
    const val PRICING = "PRO Rs. 2,500 / Year"
    // v2.2 - Universal
    var credentialsShop: String = ""
    var credentialsOffice: String = ""
    var credentialsDepartment: String = ""
    var gpsTimestamp: Boolean = true
    var gpsShare: Boolean = true
    var antiFakeGps: Boolean = true
    var wanalabWatermark: Boolean = true

    private var lastAlertTime = 0L
    private const val COOLDOWN_MS = 10000L // 1 alert per 10 sec, no spam

    private var mqttClient: MqttClient? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    // FIX v2.2: No hard-coded broker - User sets in Settings, empty = skip MQTT, snapshot still saves
    // Save with: prefs.edit().putString("mqtt_broker", "tcp://192.168.1.100:1883").apply()
    private fun getLocalBroker(context: Context): String {
        val prefs = context.getSharedPreferences("godeye_settings", Context.MODE_PRIVATE)
        return prefs.getString("mqtt_broker", "")?.trim() ?: ""
    }

    fun onFaceDetected(context: Context, results: List<FaceResult>, frameBitmap: Bitmap?) {
        val appContext = context.applicationContext
        val unknown = results.filter { !it.isKnown }
        if (unknown.isEmpty()) return

        val now = System.currentTimeMillis()
        if (now - lastAlertTime < COOLDOWN_MS) return
        lastAlertTime = now

        val first = unknown[0]

        // 1. Vibrate - Offline, always works
        vibrate(appContext)

        // 2. Save Snapshot - Offline, to phone storage
        scope.launch {
            val path = saveSnapshot(appContext, frameBitmap, first)
            // 3. MQTT Local - Only if user set broker in Settings
            val broker = getLocalBroker(appContext)
            if (broker.isNotBlank()) {
                sendMqttLocal(broker, first, path)
            }
            // 4. Online Only - Auto-send if internet
            if (NetworkAutoManager.isOnline) {
                sendMqttCloud(first, path)
            }
        }
    }

    private fun vibrate(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (e: Exception) {}
    }

    private fun saveSnapshot(context: Context, bitmap: Bitmap?, face: FaceResult): String {
        try {
            if (bitmap == null) return ""
            val dir = File(context.getExternalFilesDir(null), "GodEye_Alerts")
            if (!dir.exists()) dir.mkdirs()
            val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "unknown_${time}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            return file.absolutePath
        } catch (e: Exception) {
            return ""
        }
    }

    private fun sendMqttLocal(broker: String, face: FaceResult, snapshotPath: String) {
        try {
            if (mqttClient == null || !mqttClient!!.isConnected) {
                mqttClient = MqttClient(broker, "godeye_${System.currentTimeMillis()}", MemoryPersistence())
                val opts = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                    connectionTimeout = 2
                }
                mqttClient?.connect(opts)
            }
            val payload = """{"type":"unknown_face","cameraId":"${face.cameraId}","confidence":${face.confidence},"time":${face.timestamp},"snapshot":"$snapshotPath"}"""
            mqttClient?.publish("godeye/alert/unknown", MqttMessage(payload.toByteArray()))
        } catch (e: Exception) {
            // Offline - No broker, ignore, snapshot already saved
        }
    }

    private fun sendMqttCloud(face: FaceResult, snapshotPath: String) {
        try {
            android.util.Log.d("FaceAlert", "Online - Would send to cloud: ${face.cameraId} $snapshotPath")
        } catch (e: Exception) {}
    }

    fun getAlertHistory(context: Context): List<File> {
        val dir = File(context.getExternalFilesDir(null), "GodEye_Alerts")
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.filter { it.isFile && it.name.endsWith(".jpg") }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
