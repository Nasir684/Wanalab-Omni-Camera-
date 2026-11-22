package com.wanalab.godeye

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CameraSettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS = "godeye_cameras"
        const val KEY_1 = "camera_1_url"
        const val KEY_2 = "camera_2_url"
        const val KEY_3 = "camera_3_url"

        fun getSavedUrls(context: Context): List<String> {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return listOf(
                prefs.getString(KEY_1, "")?.trim() ?: "",
                prefs.getString(KEY_2, "")?.trim() ?: "",
                prefs.getString(KEY_3, "")?.trim() ?: ""
            )
        }

        // FIX v2.2: Auto-fix user typing - "192.168.1.10" -> "rtsp://192.168.1.10:554/stream"
        fun normalizeUrl(raw: String): String {
            var u = raw.trim()
            if (u.isBlank()) return ""
            if (!u.contains("://")) {
                if (u.matches(Regex("[0-9.]+(:[0-9]+)?(/.*)?"))) {
                    u = "rtsp://$u"
                    // FIX v2.2: Bare IP only - add default DVR port + path
                    if (u.matches(Regex("rtsp://[0-9.]+"))) {
                        u = "$u:554/stream"
                    } else if (u.matches(Regex("rtsp://[0-9.]+:[0-9]+"))) {
                        u = "$u/stream"
                    }
                } else {
                    u = "http://$u"
                }
            }
            return u
        }
    }

    private lateinit var input1: EditText
    private lateinit var input2: EditText
    private lateinit var input3: EditText
    private lateinit var hint1: TextView
    private lateinit var hint2: TextView
    private lateinit var hint3: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(32, 32, 32, 32)
        }

        val info = TextView(this).apply {
            // FIX v2.2: Removed SRT / WebRTC - NOT supported yet, don't promise
            text = "OFFLINE ALWAYS: RTSP (192.168.x.x) | MJPEG (ESP32) | Local MQTT\n" +
                   "ONLINE AUTO: HLS (.m3u8) | DASH (.mpd) | RTMP Cloud\n" +
                   "No internet? Offline cameras keep working. Internet back? Online auto-starts."
            textSize = 13f
            setPadding(0, 0, 0, 16)
        }
        root.addView(info)

        input1 = EditText(this).apply {
            hint = "Camera 1 URL - Home / Office - e.g. rtsp://192.168.1.10:554/stream"
            isSingleLine = true
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        hint1 = TextView(this).apply { textSize = 12f; setPadding(0, 4, 0, 16) }

        input2 = EditText(this).apply {
            hint = "Camera 2 URL - Home / Office"
            isSingleLine = true
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        hint2 = TextView(this).apply { textSize = 12f; setPadding(0, 4, 0, 16) }

        input3 = EditText(this).apply {
            hint = "Camera 3 URL - Home / Office"
            isSingleLine = true
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        hint3 = TextView(this).apply { textSize = 12f; setPadding(0, 4, 0, 16) }

        val saved = getSavedUrls(this)
        input1.setText(saved[0])
        input2.setText(saved[1])
        input3.setText(saved[2])

        fun updateHints() {
            hint1.text = if (input1.text.isBlank()) "" else CameraProtocolManager.getProtocolHint(input1.text.toString())
            hint2.text = if (input2.text.isBlank()) "" else CameraProtocolManager.getProtocolHint(input2.text.toString())
            hint3.text = if (input3.text.isBlank()) "" else CameraProtocolManager.getProtocolHint(input3.text.toString())
        }
        updateHints()
        input1.setOnFocusChangeListener { _, _ -> updateHints() }
        input2.setOnFocusChangeListener { _, _ -> updateHints() }
        input3.setOnFocusChangeListener { _, _ -> updateHints() }

        val saveBtn = Button(this).apply {
            text = "Save Cameras"
            setOnClickListener { save() }
        }

        val clearBtn = Button(this).apply {
            text = "Clear All"
            setOnClickListener { clear() }
        }

        root.addView(input1)
        root.addView(hint1)
        root.addView(input2)
        root.addView(hint2)
        root.addView(input3)
        root.addView(hint3)
        root.addView(saveBtn)
        root.addView(clearBtn)
        setContentView(root)
    }

    private fun save() {
        val u1 = normalizeUrl(input1.text.toString())
        val u2 = normalizeUrl(input2.text.toString())
        val u3 = normalizeUrl(input3.text.toString())

        if (u1 != input1.text.toString()) input1.setText(u1)
        if (u2 != input2.text.toString()) input2.setText(u2)
        if (u3 != input3.text.toString()) input3.setText(u3)

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_1, u1)
            .putString(KEY_2, u2)
            .putString(KEY_3, u3)
            .apply()
        Toast.makeText(this, "Saved - Returning to Viewer", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun clear() {
        input1.setText("")
        input2.setText("")
        input3.setText("")
        hint1.text = ""; hint2.text = ""; hint3.text = ""
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
    }
}
