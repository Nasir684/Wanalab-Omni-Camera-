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

    fun start(context: Context, onChange: (online: Boolean, wifi: Boolean) -> Unit) {
        listener = onChange
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Initial check
        val active = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(active)
        updateState(caps)
        onChange(isOnline, isWifi)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val c = cm.getNetworkCapabilities(network)
                updateState(c)
                onChange(isOnline, isWifi)
            }
            override fun onLost(network: Network) {
                val c = cm.getNetworkCapabilities(cm.activeNetwork)
                updateState(c)
                onChange(isOnline, isWifi)
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                updateState(caps)
                onChange(isOnline, isWifi)
            }
        })
    }

    private fun updateState(caps: NetworkCapabilities?) {
        isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    fun isOfflineProtocol(url: String): Boolean {
        val u = url.lowercase().trim()
        // LAN IPs always offline-capable
        if (u.contains("192.168.") || u.contains("10.0.") || u.contains("172.16.") || u.contains("172.17.") || u.contains("172.18.") || u.contains("localhost") || u.contains("127.0.0.1")) return true
        if (u.startsWith("rtsp://") || u.startsWith("rtmp://192.") || u.contains(".mjpg") || u.contains("mjpeg")) return true
        return false
    }

    fun isOnlineProtocol(url: String): Boolean {
        val u = url.lowercase().trim()
        if (isOfflineProtocol(u)) return false
        if (u.endsWith(".m3u8") || u.endsWith(".mpd") || u.startsWith("https://") || u.startsWith("rtmp://") && !u.contains("192.168.")) return true
        return false
    }
}
