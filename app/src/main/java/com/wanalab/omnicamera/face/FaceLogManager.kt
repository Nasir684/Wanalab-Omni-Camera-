package com.wanalab.godeye

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// WanaLab God Eye v2.2 - FaceLogManager.kt
// Face Log - Who Came When - Offline TXT Log - 30 sec Cooldown
// Author: Nasir Khan - Dera Ismail Khan, Khyber Pakhtunkhwa - 2026-11-23
// PRO Rs. 2,500 / Year

object FaceLogManager {
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

    private const val FILE_NAME = "face_log.txt"
    private const val COOLDOWN_MS = 30_000L // 30 sec - don't log same person every frame

    // personId / name -> last log time
    private val lastLogTime = mutableMapOf<String, Long>()

    @Synchronized
    fun logFaces(context: Context, results: List<FaceResult>) {
        try {
            if (results.isEmpty()) return
            val now = System.currentTimeMillis()
            val file = File(context.getExternalFilesDir(null), FILE_NAME)
            val time = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.US).format(Date(now))
            val sb = StringBuilder()
            for (r in results) {
                val key = if (r.isKnown) (r.personId ?: r.personName ?: "Known") else "Unknown"
                val last = lastLogTime[key] ?: 0L
                if (now - last < COOLDOWN_MS) continue
                lastLogTime[key] = now

                val name = if (r.isKnown) r.personName ?: "Known" else "Unknown"
                val pct = String.format(Locale.US, "%.0f%%", r.confidence * 100)
                sb.append("$time - $name - $pct - Camera: ${r.cameraId}\n")
            }
            if (sb.isNotEmpty()) {
                file.appendText(sb.toString())
            }
        } catch (e: Exception) {}
    }

    fun getLogs(context: Context): List<String> {
        try {
            val file = File(context.getExternalFilesDir(null), FILE_NAME)
            if (!file.exists()) return emptyList()
            return file.readLines().reversed()
        } catch (e: Exception) { return emptyList() }
    }

    fun clearLogs(context: Context) {
        try {
            val file = File(context.getExternalFilesDir(null), FILE_NAME)
            if (file.exists()) file.delete()
            synchronized(this) { lastLogTime.clear() }
        } catch (e: Exception) {}
    }
}
