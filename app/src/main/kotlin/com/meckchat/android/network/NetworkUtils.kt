package com.meckchat.android.network

import com.meckchat.android.core.Logger
import com.meckchat.android.model.Endpoint
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
    private const val TAG = "NetworkUtils"
    const val DEFAULT_P2P_PORT = 7788

    fun getLocalIpAddresses(): List<String> {
        val addresses = mutableListOf<String>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val host = addr.hostAddress
                        if (!host.isNullOrEmpty() && host != "127.0.0.1") {
                            addresses.add(host)
                            Logger.info(TAG, "Discovered active network IP: $host on interface ${intf.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to enumerate network interfaces: ${e.message}", e)
        }
        return addresses.distinct()
    }

    fun getLocalEndpoints(port: Int = DEFAULT_P2P_PORT): List<Endpoint> {
        val ips = getLocalIpAddresses()
        return ips.map { ip ->
            Endpoint(type = "tcp", host = ip, port = port)
        }
    }
}
