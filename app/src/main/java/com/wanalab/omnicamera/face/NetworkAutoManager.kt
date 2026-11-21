package com.godeye.app.face

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

object NetworkAutoManager {

    var isOnline = false
        private set
    var isWifi = false
        private set

    private var listener: ((Boolean, Boolean) -> Unit)? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isStarted = false

    fun start(context: Context, onChange: (online: Boolean, wifi: Boolean) -> Unit) {
        // FIX: Use applicationContext, never hold Activity
        val appContext = context.applicationContext
        listener = onChange

        // FIX: If already started, just send current state, don't register again
        if (isStarted) {
            onChange(isOnline, isWifi)
            return
        }
        isStarted = true

        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager = cm

        // Initial check
        val active = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(active)
        updateState(caps)
        onChange(isOnline, isWifi)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val c = cm.getNetworkCapabilities(network)
                // FIX: Only fire if state actually changed - prevents flap spam
                val oldOnline = isOnline
                updateState(c)
                if (oldOnline != isOnline) {
                    listener?.invoke(isOnline, isWifi)
                }
            }
            override fun onLost(network: Network) {
                val c = try { cm.getNetworkCapabilities(cm.activeNetwork) } catch (e: Exception) { null }
                val oldOnline = isOnline
                updateState(c)
                if (oldOnline != isOnline) {
                    listener?.invoke(isOnline, isWifi)
                }
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val oldOnline = isOnline
                val oldWifi = isWifi
                updateState(caps)
                if (oldOnline != isOnline || oldWifi != isWifi) {
                    listener?.invoke(isOnline, isWifi)
                }
            }
        }
        networkCallback = callback
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            e.printStackTrace()
            isStarted = false
        }
    }

    // FIX: This was missing - called by HomeCameraViewerActivity.onDestroy()
    fun stop() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (e: Exception) {}
        networkCallback = null
        connectivityManager = null
        listener = null
        isStarted = false
    }

    // FIX: Allow multiple listeners? For v1, keep single listener but don't leak.
    // If you need 2 Activities listening at once, call start() again - it will just update listener.
    // Don't call stop() in CameraActivity if Viewer is still alive - only stop in Viewer onDestroy.

    private fun updateState(caps: NetworkCapabilities?) {
        isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private fun isLanIp(url: String): Boolean {
        val u = url.lowercase().trim()
        return u.contains("192.168.") ||
               u.contains("10.0.") ||
               u.contains("10.1.") ||
               u.contains("172.16.") || u.contains("172.17.") || u.contains("172.18.") ||
               u.contains("172.19.") || u.contains("172.20.") || u.contains("172.21.") ||
               u.contains("172.22.") || u.contains("172.23.") || u.contains("172.24.") ||
               u.contains("172.25.") || u.contains("172.26.") || u.contains("172.27.") ||
               u.contains("172.28.") || u.contains("172.29.") || u.contains("172.30.") ||
               u.contains("172.31.") ||
               u.contains("localhost") || u.contains("127.0.0.1")
    }

    fun isOfflineProtocol(url: String): Boolean {
        val u = url.lowercase().trim()
        if (u.isBlank()) return false
        // FIX: LAN IP = offline, no matter what protocol
        if (isLanIp(u)) return true
        // MJPEG on LAN is offline, but MJPEG on public is online - already handled by isLanIp
        if (u.contains(".mjpg") || u.contains("mjpeg")) return isLanIp(u)
        // RTSP to LAN = offline, RTSP to public = online
        if (u.startsWith("rtsp://") || u.startsWith("rtmp://")) return isLanIp(u)
        return false
    }

    fun isOnlineProtocol(url: String): Boolean {
        val u = url.lowercase().trim()
        if (u.isBlank()) return false
        // FIX: LAN always offline, never online
        if (isLanIp(u)) return false
        if (u.endsWith(".m3u8") || u.endsWith(".mpd")) return true
        if (u.startsWith("https://") || u.startsWith("http://")) return true
        if (u.startsWith("rtmp://") || u.startsWith("rtsp://")) return true
        return false
    }
}
