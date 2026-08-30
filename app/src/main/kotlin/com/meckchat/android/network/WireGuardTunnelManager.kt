package com.meckchat.android.network

import com.meckchat.android.core.Logger

enum class TunnelState {
    DOWN,
    CONFIGURING,
    UP,
    ERROR
}

class WireGuardTunnelManager {
    var state: TunnelState = TunnelState.DOWN
        private set

    fun establishTunnel(virtualIp: String, privateKey: String): Boolean {
        Logger.info("WireGuard", "Establishing tunnel at IP $virtualIp")
        state = TunnelState.UP
        return true
    }

    fun closeTunnel() {
        Logger.info("WireGuard", "Closing tunnel")
        state = TunnelState.DOWN
    }
}
