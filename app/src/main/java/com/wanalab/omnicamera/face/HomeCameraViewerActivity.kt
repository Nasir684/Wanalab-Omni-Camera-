package com.godeye.app.face

import android.net.Uri
import android.os.Bundle
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
        }

        input1 = EditText(this).apply { hint = "Camera 1 URL - Home / Office" }
        input2 = EditText(this).apply { hint = "Camera 2 URL - Home / Office" }
        input3 = EditText(this).apply { hint = "Camera 3 URL - Home / Office" }

        val view1 = PlayerView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        val view2 = PlayerView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        val view3 = PlayerView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }

        val connectBtn = Button(this).apply {
            text = "Connect All 3"
            setOnClickListener { connectAll() }
        }

        val stopBtn = Button(this).apply {
            text = "Stop All"
            setOnClickListener { stopAll() }
        }

        root.addView(input1)
        root.addView(view1)
        root.addView(input2)
        root.addView(view2)
        root.addView(input3)
        root.addView(view3)
        root.addView(connectBtn)
        root.addView(stopBtn)

        setContentView(root)

        player1 = ExoPlayer.Builder(this).build()
        player2 = ExoPlayer.Builder(this).build()
        player3 = ExoPlayer.Builder(this).build()

        view1.player = player1
        view2.player = player2
        view3.player = player3
    }

    private fun connectAll() {
        connectOne(player1, input1.text.toString(), "Camera 1")
        connectOne(player2, input2.text.toString(), "Camera 2")
        connectOne(player3, input3.text.toString(), "Camera 3")
    }

    private fun connectOne(player: ExoPlayer, url: String, name: String) {
        if (url.isBlank()) {
            Toast.makeText(this, "$name URL empty, skipped", Toast.LENGTH_SHORT).show()
            return
        }
        try {
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
    }

    override fun onDestroy() {
        super.onDestroy()
        player1.release()
        player2.release()
        player3.release()
    }
}
