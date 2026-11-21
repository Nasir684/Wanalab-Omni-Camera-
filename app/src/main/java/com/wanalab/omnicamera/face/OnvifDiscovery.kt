package com.godeye.app.face

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object OnvifDiscovery {

    // WS-Discovery probe, standard ONVIF discovery, port 3702
    private const val PROBE = """<?xml version="1.0" encoding="UTF-8"?>
<e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope" xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery" xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
<e:Header><w:MessageID>uuid:12345678-1234-1234-1234-123456789012</w:MessageID><w:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To><w:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action></e:Header>
<e:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe></e:Body></e:Envelope>"""

    fun discover(onFound: (String) -> Unit) {
        Thread {
            try {
                val socket = DatagramSocket()
                socket.soTimeout = 5000
                val group = InetAddress.getByName("239.255.255.250")
                val packet = DatagramPacket(PROBE.toByteArray(), PROBE.length, group, 3702)
                socket.send(packet)
                val buf = ByteArray(8192)
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < 5000) {
                    try {
                        val recv = DatagramPacket(buf, buf.size)
                        socket.receive(recv)
                        val msg = String(recv.data, 0, recv.length)
                        // Extract XAddrs - contains ONVIF service URL
                        val xAddrs = Regex("http://[0-9.]+:[0-9]+/onvif/device_service")
                            .find(msg)?.value ?: continue
                        onFound(xAddrs)
                    } catch (e: Exception) { break }
                }
                socket.close()
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }
}
