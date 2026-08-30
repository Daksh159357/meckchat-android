package com.meckchat.android.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

/**
 * Native Android VpnService implementation for MeckChat.
 * Manages the WireGuard encrypted P2P network interface and tunnel routing.
 */
class MeckVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    fun startTunnel(virtualIp: String, mtu: Int = 1420) {
        if (vpnInterface != null) return

        val builder = Builder()
            .setSession("MeckChat P2P Tunnel")
            .setMtu(mtu)
            .addAddress(virtualIp, 24)

        vpnInterface = builder.establish()
    }

    fun stopTunnel() {
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }
}
