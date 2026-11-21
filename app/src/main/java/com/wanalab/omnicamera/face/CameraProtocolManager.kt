package com.godeye.app.face

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.rtmp.RtmpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource

object CameraProtocolManager {

    enum class Protocol {
        RTSP, HLS, DASH, RTMP, MJPEG, HTTP, UNKNOWN
    }

    fun detect(url: String): Protocol {
        val lower = url.lowercase().trim()
        return when {
            lower.startsWith("rtsp://") -> Protocol.RTSP
            lower.startsWith("rtmp://") -> Protocol.RTMP
            lower.endsWith(".m3u8") || lower.contains("hls") -> Protocol.HLS
            lower.endsWith(".mpd") || lower.contains("dash") -> Protocol.DASH
            lower.endsWith(".mjpg") || lower.endsWith(".mjpeg") || lower.contains("mjpeg") -> Protocol.MJPEG
            lower.startsWith("http") -> Protocol.HTTP
            else -> Protocol.UNKNOWN
        }
    }

    fun play(player: ExoPlayer, url: String) {
        val cleanUrl = url.trim()
        val uri = Uri.parse(cleanUrl)
        player.stop()
        player.clearMediaItems()

        when (detect(cleanUrl)) {
            Protocol.RTSP -> {
                val source = RtspMediaSource.Factory()
                    .setForceUseRtpTcp(true)
                    .setTimeoutMs(8000)
                    .createMediaSource(MediaItem.fromUri(uri))
                player.setMediaSource(source)
            }
            Protocol.RTMP -> {
                val factory = androidx.media3.datasource.DataSource.Factory {
                    RtmpDataSource()
                }
                val source = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(factory)
                    .createMediaSource(MediaItem.fromUri(uri))
                player.setMediaSource(source)
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
            else -> {
                // MJPEG, HTTP, MP4, fallback - ExoPlayer auto-detects
                player.setMediaItem(MediaItem.fromUri(uri))
            }
        }
        player.prepare()
        player.play()
    }

    fun getProtocolHint(url: String): String {
        return when (detect(url)) {
            Protocol.RTSP -> "RTSP - Low latency, local camera"
            Protocol.HLS -> "HLS - Remote / cloud, 5-10s delay"
            Protocol.DASH -> "DASH - Adaptive, low bandwidth"
            Protocol.RTMP -> "RTMP - Push / old NVR"
            Protocol.MJPEG -> "MJPEG - Old camera / ESP32-CAM"
            Protocol.HTTP -> "HTTP - Snapshot / MP4 / TS"
            Protocol.UNKNOWN -> "Auto - Trying ExoPlayer default"
        }
    }
}
