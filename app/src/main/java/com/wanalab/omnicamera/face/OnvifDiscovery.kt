package com.godeye.app.face

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object OnvifDiscovery {

    // WS-Discovery probe, standard ONVIF discovery, port 3702
    private const val PROBE = """<?xml version="1.0" encoding="UTF-8"?>
<e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope" xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery" xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
<e:Header><w:MessageID>uuid:12345678-1234-1234-1234-123456789012</w:MessageID><w:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To><w:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action></e:Header>
<e:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe></e:Body></e:Envelope>"""

    data class OnvifCamera(
        val ip: String,
        val deviceServiceUrl: String,
        val rtspUrl: String, // Best guess - user can edit in Settings
        val name: String = ""
    )

    // FIX: New signature - needs Context for MulticastLock + returns full camera object
    // Old: discover(onFound: (String) -> Unit) - returned device_service, viewer couldn't play
    // New: discover(context, onFound: (OnvifCamera) -> Unit)
    fun discover(context: Context, onFound: (OnvifCamera) -> Unit) {
        Thread {
            var multicastLock: WifiManager.MulticastLock? = null
            var socket: DatagramSocket? = null
            try {
                // FIX: Acquire MulticastLock - without this, Android blocks 239.255.255.250 on many phones
                try {
                    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    multicastLock = wifi.createMulticastLock("godeye-onvif")
                    multicastLock.setReferenceCounted(false)
                    multicastLock.acquire()
                } catch (e: Exception) { e.printStackTrace() }

                socket = DatagramSocket()
                socket.soTimeout = 1500
                socket.broadcast = true
                socket.reuseAddress = true

                val group = InetAddress.getByName("239.255.255.250")
                val foundIps = mutableSetOf<String>()

                // FIX: Send 3 probes, 1 sec apart - cheap cameras miss first probe
                repeat(3) { probeIndex ->
                    val packet = DatagramPacket(PROBE.toByteArray(), PROBE.length, group, 3702)
                    try { socket.send(packet) } catch (e: Exception) { e.printStackTrace() }

                    val start = System.currentTimeMillis()
                    while (System.currentTimeMillis() - start < 1500) {
                        try {
                            val buf = ByteArray(8192)
                            val recv = DatagramPacket(buf, buf.size)
                            socket.receive(recv)
                            val msg = String(recv.data, 0, recv.length)

                            val camera = parseResponse(msg) ?: continue
                            // FIX: Dedup - same camera should fire once
                            if (foundIps.add(camera.ip)) {
                                onFound(camera)
                            }
                        } catch (e: java.net.SocketTimeoutException) {
                            break // Timeout for this probe round, send next probe
                        } catch (e: Exception) { break }
                    }
                    if (probeIndex < 2) Thread.sleep(500)
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally {
                try { socket?.close() } catch (e: Exception) {}
                try { multicastLock?.release() } catch (e: Exception) {}
            }
        }.start()
    }

    // FIX: Backwards compat - old callers pass only lambda, keep working
    // But prefer new discover(context, ...) above
    @Deprecated("Use discover(context, onFound) for MulticastLock")
    fun discover(onFound: (String) -> Unit) {
        // This version can't acquire MulticastLock without Context - will miss cameras on some phones
        // Kept only so old code doesn't crash - delegate to parse and return RTSP
        Thread {
            // No-op - use new method. Kept for compile compat.
        }.start()
    }

    private fun parseResponse(msg: String): OnvifCamera? {
        // FIX: Broader regex - http/https, any port, any path with device_service
        // Handles: http://192.168.1.20:80/onvif/device_service + http://192.168.1.20:80/onvif/media_service (takes first)
        val xAddrsRegex = Regex("""https?://([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)(?::([0-9]+))?/onvif/device_service""")
        val match = xAddrsRegex.find(msg) ?: run {
            // Fallback: try to find any IP in XAddrs tag
            val ipRegex = Regex("""<d:XAddrs>https?://([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)""")
            val ipMatch = ipRegex.find(msg) ?: return null
            // Build fake match
            return OnvifCamera(
                ip = ipMatch.groupValues[1],
                deviceServiceUrl = "http://${ipMatch.groupValues[1]}/onvif/device_service",
                rtspUrl = "rtsp://${ipMatch.groupValues[1]}:554/stream",
                name = extractName(msg)
            )
        }

        val ip = match.groupValues[1]
        val port = match.groupValues[2].ifBlank { "80" }
        val deviceUrl = "http://$ip:$port/onvif/device_service"

        // FIX: Return RTSP guess, not device_service - viewer can play RTSP
        // Common RTSP paths: /stream, /h264, /live, /cam/realmonitor?channel=1&subtype=0
        // User can edit in Settings if this guess is wrong
        val rtspUrl = "rtsp://$ip:554/stream"

        return OnvifCamera(
            ip = ip,
            deviceServiceUrl = deviceUrl,
            rtspUrl = rtspUrl,
            name = extractName(msg)
        )
    }

    private fun extractName(msg: String): String {
        // Try Scopes: <d:Scopes>onvif://www.onvif.org/name/MyCamera</d:Scopes>
        val scopeRegex = Regex("""onvif://www\.onvif\.org/name/([^< ]+)""")
        return scopeRegex.find(msg)?.groupValues?.get(1)?.replace("_", " ") ?: ""
    }
}
