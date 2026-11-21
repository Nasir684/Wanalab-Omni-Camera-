package com.godeye.app.face

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class HomeCameraViewerActivity : AppCompatActivity() {

    private lateinit var player1: ExoPlayer
    private lateinit var player2: ExoPlayer
    private lateinit var player3: ExoPlayer

    private lateinit var input1: EditText
    private lateinit var input2: EditText
    private lateinit var input3: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(16, 16, 16, 16)
        }

        input1 = EditText(this).apply { hint = "Camera 1 URL - Home / Office" }
        input2 = EditText(this).apply { hint = "Camera 2 URL - Home / Office" }
        input3 = EditText(this).apply { hint = "Camera 3 URL - Home / Office" }

        // Auto-load saved URLs from Settings
        val saved = CameraSettingsActivity.getSavedUrls(this)
        input1.setText(saved[0])
        input2.setText(saved[1])
        input3.setText(saved[2])

        val view1 = PlayerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        val view2 = PlayerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        val view3 = PlayerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }

        val connectBtn = Button(this).apply {
            text = "Connect All 3"
            setOnClickListener { connectAll() }
        }

        val stopBtn = Button(this).apply {
            text = "Stop All"
            setOnClickListener { stopAll() }
        }

        val settingsBtn = Button(this).apply {
            text = "Settings - Save URLs"
            setOnClickListener {
                startActivity(Intent(this@HomeCameraViewerActivity, CameraSettingsActivity::class.java))
            }
        }

        root.addView(input1)
        root.addView(view1)
        root.addView(input2)
        root.addView(view2)
        root.addView(input3)
        root.addView(view3)
        root.addView(connectBtn)
        root.addView(stopBtn)
        root.addView(settingsBtn)

        setContentView(root)

        player1 = ExoPlayer.Builder(this).build()
        player2 = ExoPlayer.Builder(this).build()
        player3 = ExoPlayer.Builder(this).build()

        view1.player = player1
        view2.player = player2
        view3.player = player3
    }

    override fun onResume() {
        super.onResume()
        // Reload after coming back from Settings
        val saved = CameraSettingsActivity.getSavedUrls(this)
        if (input1.text.toString() != saved[0]) input1.setText(saved[0])
        if (input2.text.toString() != saved[1]) input2.setText(saved[1])
        if (input3.text.toString() != saved[2]) input3.setText(saved[2])
    }

    override fun onPause() {
        super.onPause()
        player1.pause()
        player2.pause()
        player3.pause()
    }

    private fun connectAll() {
        connectOne(player1, input1.text.toString().trim(), "Camera 1")
        connectOne(player2, input2.text.toString().trim(), "Camera 2")
        connectOne(player3, input3.text.toString().trim(), "Camera 3")
    }

    private fun connectOne(player: ExoPlayer, url: String, name: String) {
        if (url.isBlank()) {
            Toast.makeText(this, "$name URL empty, skipped", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            player.prepare()
            player.play()
        } catch (e: Exception) {
            Toast.makeText(this, "$name failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAll() {
        player1.stop()
        player2.stop()
        player3.stop()
        player1.clearMediaItems()
        player2.clearMediaItems()
        player3.clearMediaItems()
    }

    override fun onDestroy() {
        super.onDestroy()
        player1.release()
        player2.release()
        player3.release()
    }
}
