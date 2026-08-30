package com.meckchat.android.network

import com.meckchat.android.core.Logger
import com.meckchat.android.model.P2PFrame
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class P2PSocketClient {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    fun connect(virtualIp: String, port: Int = 7788) {
        Logger.info("P2PSocket", "Connecting P2P socket to $virtualIp:$port")
    }

    fun sendFrame(frame: P2PFrame): Boolean {
        return try {
            val encoded = frame.encode()
            outputStream?.write(encoded)
            outputStream?.flush()
            true
        } catch (e: Exception) {
            Logger.error("P2PSocket", "Failed to send frame", e)
            false
        }
    }

    fun disconnect() {
        socket?.close()
        socket = null
    }
}
