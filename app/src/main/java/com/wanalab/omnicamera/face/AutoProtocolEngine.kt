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
    private var lastOnline: Boolean? = null
    private var lastStartedUrls: List<String> = emptyList()

    fun register(player: ExoPlayer, url: String, name: String) {
        slots.removeAll { it.player == player }
        if (url.isNotBlank()) {
            slots.add(CameraSlot(player, url, name))
        }
    }

    fun clear() {
        slots.clear()
        lastStartedUrls = emptyList()
        lastOnline = null
    }

    // Called on every network change + on Connect
    fun autoStartAll(context: Context, isOnline: Boolean, force: Boolean = false) {
        // FIX: Skip duplicate network events - prevents flicker on WiFi flap
        val currentUrls = slots.map { it.url }
        if (!force && lastOnline == isOnline && currentUrls == lastStartedUrls && slots.isNotEmpty()) {
            return
        }
        lastOnline = isOnline
        lastStartedUrls = currentUrls

        for (slot in slots) {
            val url = slot.url
            if (url.isBlank()) continue

            // FIX: Skip if already playing same URL - no re-buffer
            if (isAlreadyPlaying(slot.player, url)) {
                if (NetworkAutoManager.isOnlineProtocol(url) && !isOnline) {
                    try { slot.player.pause(); slot.player.stop() } catch (e: Exception) {}
                }
                continue
            }

            val offlineOk = NetworkAutoManager.isOfflineProtocol(url)
            val onlineOnly = NetworkAutoManager.isOnlineProtocol(url)

            when {
                offlineOk -> {
                    safePlay(slot.player, url)
                }
                onlineOnly && isOnline -> {
                    safePlay(slot.player, url)
                }
                onlineOnly && !isOnline -> {
                    try { slot.player.pause(); slot.player.stop(); slot.player.clearMediaItems() } catch (e: Exception) {}
                }
                else -> {
                    safePlay(slot.player, url)
                }
            }
        }
    }

    fun onNetworkRestored(context: Context) {
        autoStartAll(context, true, force = true)
    }

    private fun isAlreadyPlaying(player: ExoPlayer, url: String): Boolean {
        return try {
            val current = player.currentMediaItem?.localConfiguration?.uri?.toString()
            current != null && (current == url || current.removeSuffix("/") == url.removeSuffix("/"))
        } catch (e: Exception) {
            false
        }
    }

    private fun safePlay(player: ExoPlayer, url: String) {
        try {
            CameraProtocolManager.play(player, url)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
