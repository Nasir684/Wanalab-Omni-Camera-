package com.wanalab.godeye

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource

object CameraProtocolManager {

    private const val TAG = "CameraProtocol"

    enum class Protocol {
        RTSP, HLS, DASH, RTMP, MJPEG, HTTP, UNKNOWN
    }

    fun detect(url: String): Protocol {
        val lower = url.lowercase().trim()
        return when {
            lower.startsWith("rtsp://") -> Protocol.RTSP
            lower.startsWith("rtmp://") -> Protocol.RTMP
            lower.endsWith(".m3u8") || lower.contains("/hls") || lower.contains("hls") -> Protocol.HLS
            lower.endsWith(".mpd") || lower.contains("dash") -> Protocol.DASH
            lower.endsWith(".mjpg") || lower.endsWith(".mjpeg") || lower.contains("mjpeg") -> Protocol.MJPEG
            lower.startsWith("http") -> Protocol.HTTP
            else -> Protocol.UNKNOWN
        }
    }

    fun play(player: ExoPlayer, url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        val uri = Uri.parse(cleanUrl)

        // FIX: Don't stop if already playing same URL - prevents flicker
        try {
            val current = player.currentMediaItem?.localConfiguration?.uri?.toString()
            if (current != null && current.removeSuffix("/") == cleanUrl.removeSuffix("/")) {
                if (!player.isPlaying) player.play()
                return
            }
        } catch (e: Exception) {}

        try {
            player.stop()
            player.clearMediaItems()
        } catch (e: Exception) {}

        when (detect(cleanUrl)) {
            Protocol.RTSP -> {
                // FIX: Don't force TCP - let ExoPlayer auto-negotiate TCP/UDP
                // Cheap DVRs only support UDP, forcing TCP = black screen
                val source = RtspMediaSource.Factory()
                    .setForceUseRtpTcp(false)
                    .setTimeoutMs(10000)
                    .createMediaSource(MediaItem.fromUri(uri))
                player.setMediaSource(source)
            }
            Protocol.RTMP -> {
                // FIX: RTMP via Progressive + DefaultHttpDataSource - RtmpDataSource removed in Media3 1.4+
                try {
                    val factory = DefaultHttpDataSource.Factory()
                    val source = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(factory)
                        .createMediaSource(MediaItem.fromUri(uri))
                    player.setMediaSource(source)
                } catch (e: Exception) {
                    Log.e(TAG, "RTMP failed: $e")
                    player.setMediaItem(MediaItem.fromUri(uri))
                }
            }
            Protocol.HLS -> {
                val source = HlsMediaSource.Factory(DefaultHttpDataSource.Factory())
                    .createMediaSource(MediaItem.fromUri(uri))
                player.setMediaSource(source)
            }
            Protocol.DASH -> {
                val source = DashMediaSource.Factory(DefaultHttpDataSource.Factory())
                    .createMediaSource(MediaItem.fromUri(uri))
                player.setMediaSource(source)
            }
            Protocol.MJPEG -> {
                // FIX: ExoPlayer cannot play MJPEG - log, don't crash
                // TODO v2: Use MjpegView (ImageView + OkHttp thread) for ESP32-CAM
                Log.w(TAG, "MJPEG not supported by ExoPlayer: $cleanUrl - Use MjpegView in v2")
                player.setMediaItem(MediaItem.fromUri(uri))
            }
            else -> {
                player.setMediaItem(MediaItem.fromUri(uri))
            }
        }
        try {
            player.prepare()
            player.play()
        } catch (e: Exception) {
            Log.e(TAG, "play failed $cleanUrl: $e")
        }
    }

    fun getProtocolHint(url: String): String {
        return when (detect(url)) {
            Protocol.RTSP -> "RTSP - Low latency, local camera"
            Protocol.HLS -> "HLS - Remote / cloud, 5-10s delay"
            Protocol.DASH -> "DASH - Adaptive, low bandwidth"
            Protocol.RTMP -> "RTMP - Push / old NVR"
            Protocol.MJPEG -> "MJPEG - Old camera / ESP32-CAM - Needs MjpegView v2"
            Protocol.HTTP -> "HTTP - Snapshot / MP4 / TS"
            Protocol.UNKNOWN -> "Auto - Trying ExoPlayer default"
        }
    }
}
