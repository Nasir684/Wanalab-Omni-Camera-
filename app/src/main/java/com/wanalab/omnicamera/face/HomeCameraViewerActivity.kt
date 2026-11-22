package com.wanalab.godeye

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

    private var lastConnectedUrls: List<String> = emptyList()
    private var isFirstResume = true

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
            setOnClickListener { connectAll(force = true) }
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

        NetworkAutoManager.start(this) { online, wifi ->
            runOnUiThread {
                statusText.text = if (online) {
                    if (wifi) "ONLINE - WiFi - All protocols ON" else "ONLINE - Mobile - All protocols ON"
                } else {
                    "OFFLINE - LAN Only - RTSP / ONVIF / MJPEG ON, HLS / Cloud Paused"
                }
                if (lastConnectedUrls.isNotEmpty()) {
                    connectAll(force = true)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::input1.isInitialized) return
        val saved = CameraSettingsActivity.getSavedUrls(this)
        if (input1.text.toString() != saved[0]) input1.setText(saved[0])
        if (input2.text.toString() != saved[1]) input2.setText(saved[1])
        if (input3.text.toString() != saved[2]) input3.setText(saved[2])

        val current = listOf(input1.text.toString().trim(), input2.text.toString().trim(), input3.text.toString().trim())
        if (isFirstResume || current != lastConnectedUrls) {
            isFirstResume = false
            connectAll(force = false)
        }
        if (::player1.isInitialized && player1.playbackState != ExoPlayer.STATE_IDLE) player1.play()
        if (::player2.isInitialized && player2.playbackState != ExoPlayer.STATE_IDLE) player2.play()
        if (::player3.isInitialized && player3.playbackState != ExoPlayer.STATE_IDLE) player3.play()
    }

    override fun onPause() {
        super.onPause()
        if (::player1.isInitialized) player1.pause()
        if (::player2.isInitialized) player2.pause()
        if (::player3.isInitialized) player3.pause()
    }

    private fun connectAll(force: Boolean = false) {
        val u1 = input1.text.toString().trim()
        val u2 = input2.text.toString().trim()
        val u3 = input3.text.toString().trim()
        val current = listOf(u1, u2, u3)

        if (!force && current == lastConnectedUrls) return
        if (u1.isBlank() && u2.isBlank() && u3.isBlank()) {
            statusText.text = "No camera URLs - Go to Settings or Discover ONVIF"
            return
        }

        AutoProtocolEngine.clear()
        if (u1.isNotBlank()) AutoProtocolEngine.register(player1, u1, "Camera 1")
        if (u2.isNotBlank()) AutoProtocolEngine.register(player2, u2, "Camera 2")
        if (u3.isNotBlank()) AutoProtocolEngine.register(player3, u3, "Camera 3")

        AutoProtocolEngine.autoStartAll(this, NetworkAutoManager.isOnline)
        lastConnectedUrls = current

        Toast.makeText(this, "Auto Protocol: Offline always ON, Online auto when internet", Toast.LENGTH_SHORT).show()
    }

    private fun discoverOnvif() {
        Toast.makeText(this, "Scanning LAN for ONVIF cameras... Offline", Toast.LENGTH_SHORT).show()
        OnvifDiscovery.discover { onvifUrl ->
            runOnUiThread {
                Toast.makeText(this, "Found ONVIF: $onvifUrl", Toast.LENGTH_LONG).show()
                if (input1.text.isBlank()) input1.setText(onvifUrl)
                else if (input2.text.isBlank()) input2.setText(onvifUrl)
                else if (input3.text.isBlank()) input3.setText(onvifUrl)
                else Toast.makeText(this, "All 3 slots full - Replace one manually", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopAll() {
        if (::player1.isInitialized) { player1.stop(); player1.clearMediaItems() }
        if (::player2.isInitialized) { player2.stop(); player2.clearMediaItems() }
        if (::player3.isInitialized) { player3.stop(); player3.clearMediaItems() }
        lastConnectedUrls = emptyList()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { NetworkAutoManager.stop() } catch (e: Exception) {}
        try { AutoProtocolEngine.clear() } catch (e: Exception) {}
        try { if (::player1.isInitialized) player1.release() } catch (e: Exception) {}
        try { if (::player2.isInitialized) player2.release() } catch (e: Exception) {}
        try { if (::player3.isInitialized) player3.release() } catch (e: Exception) {}
    }
}
