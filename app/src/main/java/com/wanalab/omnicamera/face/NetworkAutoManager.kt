package com.wanalab.godeye

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.util.concurrent.CopyOnWriteArraySet

// WanaLab God Eye v2.2 - NetworkAutoManager.kt
// Network Auto - Online / Offline + WiFi Detect - LAN vs Cloud Protocol
// Author: Nasir Khan - Dera Ismail Khan, Khyber Pakhtunkhwa - 2026-11-23
// PRO Rs. 2,500 / Year

object NetworkAutoManager {

    const val VERSION = "2.2"
    const val APP_NAME = "WanaLab God Eye"
    const val PRICING = "PRO Rs. 2,500 / Year"
    // v2.2 - Universal
    var credentialsShop: String = ""
    var credentialsOffice: String = ""
    var credentialsDepartment: String = ""
    var gpsTimestamp: Boolean = true
    var gpsShare: Boolean = true
    var antiFakeGps: Boolean = true
    var wanalabWatermark: Boolean = true

    var isOnline = false
        private set
    var isWifi = false
        private set

    private val listeners = CopyOnWriteArraySet<(Boolean, Boolean) -> Unit>()
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isStarted = false

    fun start(context: Context, onChange: (online: Boolean, wifi: Boolean) -> Unit) {
        // FIXED: Use applicationContext, never hold Activity
        val appContext = context.applicationContext
        listeners.add(onChange)

        // FIXED: If already started, just send current state, don't register again
        if (isStarted) {
            onChange(isOnline, isWifi)
            return
        }
        isStarted = true

        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager = cm

        // Initial check
        val active = cm.activeNetwork
        val caps = try { cm.getNetworkCapabilities(active) } catch (e: Exception) { null }
        updateState(caps)
        notifyListeners()

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val c = try { cm.getNetworkCapabilities(network) } catch (e: Exception) { null }
                val oldOnline = isOnline
                val oldWifi = isWifi
                updateState(c)
                if (oldOnline != isOnline || oldWifi != isWifi) notifyListeners()
            }
            override fun onLost(network: Network) {
                val c = try { cm.getNetworkCapabilities(cm.activeNetwork) } catch (e: Exception) { null }
                val oldOnline = isOnline
                val oldWifi = isWifi
                updateState(c)
                if (oldOnline != isOnline || oldWifi != isWifi) notifyListeners()
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val oldOnline = isOnline
                val oldWifi = isWifi
                updateState(caps)
                if (oldOnline != isOnline || oldWifi != isWifi) notifyListeners()
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

    private fun notifyListeners() {
        for (l in listeners) {
            try { l(isOnline, isWifi) } catch (e: Exception) {}
        }
    }

    // FIXED: stop() with no arg = remove all + unregister. Use this only in last Activity.
    fun stop() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (e: Exception) {}
        networkCallback = null
        connectivityManager = null
        listeners.clear()
        isStarted = false
    }

    // FIXED: Remove one listener - call this in onDestroy of each Activity that called start()
    // So CameraActivity stopping won't kill ViewerActivity listener.
    fun stop(onChange: (Boolean, Boolean) -> Unit) {
        listeners.remove(onChange)
        if (listeners.isEmpty()) stop()
    }

    private fun updateState(caps: NetworkCapabilities?) {
        isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private fun isLanIp(url: String): Boolean {
        val u = url.lowercase().trim()
        if (u.contains("localhost") || u.contains("127.0.0.1")) return true
        if (u.contains("192.168.")) return true
        if (u.contains("10.")) return true
        // FIXED: 172.16.0.0 - 172.31.255.255, old code missed 172.16. / listed manually
        val regex = Regex("172\\.(1[6-9]|2[0-9]|3[0-1])\\.")
        if (regex.containsMatchIn(u)) return true
        return false
    }

    fun isOfflineProtocol(url: String): Boolean {
        val u = url.lowercase().trim()
        if (u.isBlank()) return false
        // FIXED: LAN IP = offline, no matter what protocol
        if (isLanIp(u)) return true
        return false
    }

    fun isOnlineProtocol(url: String): Boolean {
        val u = url.lowercase().trim()
        if (u.isBlank()) return false
        // FIXED: LAN always offline, never online
        if (isLanIp(u)) return false
        if (u.endsWith(".m3u8") || u.endsWith(".mpd")) return true
        if (u.startsWith("https://") || u.startsWith("http://")) return true
        if (u.startsWith("rtmp://") || u.startsWith("rtsp://")) return true
        return false
    }
}
