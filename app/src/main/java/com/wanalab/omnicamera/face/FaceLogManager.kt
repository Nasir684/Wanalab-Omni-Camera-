package com.godeye.app.face

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FaceLogManager {
    private const val FILE_NAME = "face_log.txt"

    fun logFaces(context: Context, results: List<FaceResult>) {
        try {
            // Cooldown - don't log same person every 300ms, log once per 30 sec
            val file = File(context.getExternalFilesDir(null), FILE_NAME)
            val time = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.US).format(Date())
            val sb = StringBuilder()
            for (r in results) {
                val name = if (r.isKnown) r.personName ?: "Known" else "Unknown"
                val pct = String.format("%.0f%%", r.confidence * 100)
                sb.append("$time - $name - $pct - Camera: ${r.cameraId}\n")
            }
            file.appendText(sb.toString())
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
        } catch (e: Exception) {}
    }
}
