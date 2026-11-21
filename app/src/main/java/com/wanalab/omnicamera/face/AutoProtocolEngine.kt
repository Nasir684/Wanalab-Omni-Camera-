package com.godeye.app.face

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

data class CameraSlot(
    val player: ExoPlayer,
    val url: String,
    val name: String
)

object AutoProtocolEngine {

    private val slots = mutableListOf<CameraSlot>()
    private var lastOnline = false

    fun register(player: ExoPlayer, url: String, name: String) {
        slots.removeAll { it.player == player }
        if (url.isNotBlank()) slots.add(CameraSlot(player, url, name))
    }

    fun clear() = slots.clear()

    // Called on every network change + on Connect
    fun autoStartAll(context: Context, isOnline: Boolean) {
        lastOnline = isOnline
        for (slot in slots) {
            val url = slot.url
            if (url.isBlank()) continue

            val offlineOk = NetworkAutoManager.isOfflineProtocol(url)
            val onlineOnly = NetworkAutoManager.isOnlineProtocol(url)

            when {
                // Offline protocol - ALWAYS play, even with no internet
                offlineOk -> {
                    CameraProtocolManager.play(slot.player, url)
                }
                // Online protocol - ONLY when internet is back
                onlineOnly && isOnline -> {
                    CameraProtocolManager.play(slot.player, url)
                }
                // Online protocol but offline - pause, don't crash, wait for internet
                onlineOnly && !isOnline -> {
                    slot.player.pause()
                    slot.player.stop()
                }
                // Unknown - try anyway, ExoPlayer will decide
                else -> {
                    CameraProtocolManager.play(slot.player, url)
                }
            }
        }
    }

    fun onNetworkRestored(context: Context) {
        // Internet came back - auto-start online protocols only
        autoStartAll(context, true)
    }
}
