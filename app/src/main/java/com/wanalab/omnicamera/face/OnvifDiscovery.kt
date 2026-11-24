package com.wanalab.godeye

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

// WanaLab God Eye v2.2 - OnvifDiscovery.kt
// ONVIF Discovery - WS-Discovery Probe - LAN Offline - RTSP Auto
// Author: Nasir Khan - Dera Ismail Khan, Khyber Pakhtunkhwa - 2026-11-23
// PRO Rs. 2,500 / Year

object OnvifDiscovery {

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

    private const val PROBE = """<?xml version="1.0" encoding="UTF-8"?>
<e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope" xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery" xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
<e:Header><w:MessageID>uuid:12345678-1234-1234-1234-123456789012</w:MessageID><w:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To><w:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action></e:Header>
<e:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe></e:Body></e:Envelope>"""

    data class OnvifCamera(
        val ip: String,
        val deviceServiceUrl: String,
        val rtspUrl: String,
        val name: String = ""
    )

    fun discover(context: Context, onFound: (OnvifCamera) -> Unit) {
        discoverInternal(context, onFound)
    }

    // FIXED: Old signature now actually works - returns RTSP, not device_service
    // HomeCameraViewerActivity still calls discover { url -> } - it will get playable RTSP
    fun discover(onFound: (String) -> Unit) {
        discoverInternal(null) { camera ->
            onFound(camera.rtspUrl)
        }
    }

    private fun discoverInternal(context: Context?, onFound: (OnvifCamera) -> Unit) {
        Thread {
            var multicastLock: WifiManager.MulticastLock? = null
            var socket: DatagramSocket? = null
            try {
                if (context != null) {
                    try {
                        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        multicastLock = wifi.createMulticastLock("godeye-onvif")
                        multicastLock.setReferenceCounted(false)
                        multicastLock.acquire()
                    } catch (e: Exception) {}
                }

                socket = DatagramSocket()
                socket.soTimeout = 1500
                socket.broadcast = true
                socket.reuseAddress = true

                val group = InetAddress.getByName("239.255.255.250")
                val foundIps = mutableSetOf<String>()

                repeat(3) { probeIndex ->
                    val bytes = PROBE.toByteArray(Charsets.UTF_8)
                    val packet = DatagramPacket(bytes, bytes.size, group, 3702)
                    try { socket.send(packet) } catch (e: Exception) {}

                    val start = System.currentTimeMillis()
                    while (System.currentTimeMillis() - start < 1500) {
                        try {
                            val buf = ByteArray(8192)
                            val recv = DatagramPacket(buf, buf.size)
                            socket.receive(recv)
                            val msg = String(recv.data, 0, recv.length, Charsets.UTF_8)
                            val camera = parseResponse(msg) ?: continue
                            if (foundIps.add(camera.ip)) {
                                onFound(camera)
                            }
                        } catch (e: java.net.SocketTimeoutException) {
                            break
                        } catch (e: Exception) { break }
                    }
                    if (probeIndex < 2) Thread.sleep(500)
                }
            } catch (e: Exception) {}
            finally {
                try { socket?.close() } catch (e: Exception) {}
                try { if (multicastLock?.isHeld == true) multicastLock.release() } catch (e: Exception) {}
            }
        }.start()
    }

    private fun parseResponse(msg: String): OnvifCamera? {
        val xAddrsRegex = Regex("""https?://([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)(?::([0-9]+))?/onvif/device_service""")
        val match = xAddrsRegex.find(msg) ?: run {
            val ipRegex = Regex("""<d:XAddrs>https?://([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)""")
            val ipMatch = ipRegex.find(msg) ?: return null
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
        val rtspUrl = "rtsp://$ip:554/stream"

        return OnvifCamera(
            ip = ip,
            deviceServiceUrl = deviceUrl,
            rtspUrl = rtspUrl,
            name = extractName(msg)
        )
    }

    private fun extractName(msg: String): String {
        val scopeRegex = Regex("""onvif://www\.onvif\.org/name/([^< ]+)""")
        return scopeRegex.find(msg)?.groupValues?.get(1)?.replace("_", " ") ?: ""
    }
}
