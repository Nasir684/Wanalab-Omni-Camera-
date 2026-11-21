package com.godeye.app.face

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class HomeCameraViewerActivity : AppCompatActivity() {

    private lateinit var player1: ExoPlayer
    private lateinit var player2: ExoPlayer
    private lateinit var player3: ExoPlayer
    private lateinit var input1: EditText
    private lateinit var input2: EditText
    private lateinit var input3: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setPadding(16, 16, 16, 16)
        }

        statusText = TextView(this).apply {
            text = "Checking network..."
            textSize = 14f
        }

        input1 = EditText(this).apply { hint = "Camera 1 URL - RTSP / HLS / MJPEG" }
        input2 = EditText(this).apply { hint = "Camera 2 URL - RTSP / HLS / MJPEG" }
        input3 = EditText(this).apply { hint = "Camera 3 URL - RTSP / HLS / MJPEG" }

        val saved = CameraSettingsActivity.getSavedUrls(this)
        input1.setText(saved[0]); input2.setText(saved[1]); input3.setText(saved[2])

        val view1 = PlayerView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        val view2 = PlayerView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        val view3 = PlayerView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }

        val connectBtn = Button(this).apply {
            text = "Connect All - Auto Protocol"
            setOnClickListener { connectAll() }
        }
        val stopBtn = Button(this).apply {
            text = "Stop All"
            setOnClickListener { stopAll() }
        }
        val settingsBtn = Button(this).apply {
            text = "Settings - Save URLs + Discover ONVIF"
            setOnClickListener {
                startActivity(Intent(this@HomeCameraViewerActivity, CameraSettingsActivity::class.java))
            }
        }
        val discoverBtn = Button(this).apply {
            text = "Discover ONVIF Cameras - Offline"
            setOnClickListener { discoverOnvif() }
        }

        root.addView(statusText)
        root.addView(input1); root.addView(view1)
        root.addView(input2); root.addView(view2)
        root.addView(input3); root.addView(view3)
        root.addView(connectBtn); root.addView(stopBtn)
        root.addView(discoverBtn); root.addView(settingsBtn)
        setContentView(root)

        player1 = ExoPlayer.Builder(this).build()
        player2 = ExoPlayer.Builder(this).build()
        player3 = ExoPlayer.Builder(this).build()
        view1.player = player1; view2.player = player2; view3.player = player3

        // AUTO NETWORK LISTENER - Offline / Online switch
        NetworkAutoManager.start(this) { online, wifi ->
            runOnUiThread {
                statusText.text = if (online) {
                    if (wifi) "ONLINE - WiFi - All protocols ON" else "ONLINE - Mobile - All protocols ON"
                } else {
                    "OFFLINE - LAN Only - RTSP / ONVIF / MJPEG ON, HLS / Cloud Paused"
                }
                // Auto-start online protocols when internet returns
                if (online) {
                    AutoProtocolEngine.autoStartAll(this, true)
                    Toast.makeText(this, "Internet back - Online protocols auto-started", Toast.LENGTH_SHORT).show()
                } else {
                    // Keep offline running, pause online only
                    AutoProtocolEngine.autoStartAll(this, false)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val saved = CameraSettingsActivity.getSavedUrls(this)
        if (input1.text.toString() != saved[0]) input1.setText(saved[0])
        if (input2.text.toString() != saved[1]) input2.setText(saved[1])
        if (input3.text.toString() != saved[2]) input3.setText(saved[2])
        // Auto-reconnect on return from Settings, respects offline/online
        connectAll()
    }

    override fun onPause() {
        super.onPause()
        player1.pause(); player2.pause(); player3.pause()
    }

    private fun connectAll() {
        val u1 = input1.text.toString().trim()
        val u2 = input2.text.toString().trim()
        val u3 = input3.text.toString().trim()

        AutoProtocolEngine.clear()
        AutoProtocolEngine.register(player1, u1, "Camera 1")
        AutoProtocolEngine.register(player2, u2, "Camera 2")
        AutoProtocolEngine.register(player3, u3, "Camera 3")

        // This respects offline / online automatically
        AutoProtocolEngine.autoStartAll(this, NetworkAutoManager.isOnline)

        Toast.makeText(this, "Auto Protocol: Offline always ON, Online auto when internet", Toast.LENGTH_SHORT).show()
    }

    private fun discoverOnvif() {
        Toast.makeText(this, "Scanning LAN for ONVIF cameras... Offline", Toast.LENGTH_SHORT).show()
        OnvifDiscovery.discover { onvifUrl ->
            runOnUiThread {
                Toast.makeText(this, "Found ONVIF: $onvifUrl", Toast.LENGTH_LONG).show()
                // Auto-fill first empty slot
                if (input1.text.isBlank()) input1.setText(onvifUrl)
                else if (input2.text.isBlank()) input2.setText(onvifUrl)
                else if (input3.text.isBlank()) input3.setText(onvifUrl)
            }
        }
    }

    private fun stopAll() {
        player1.stop(); player2.stop(); player3.stop()
        player1.clearMediaItems(); player2.clearMediaItems(); player3.clearMediaItems()
    }

    override fun onDestroy() {
        super.onDestroy()
        player1.release(); player2.release(); player3.release()
    }
}
