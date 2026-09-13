package com.meckchat.android.network

import com.meckchat.android.core.Logger
import com.meckchat.android.model.ChatMessage
import com.meckchat.android.model.FrameType
import com.meckchat.android.model.P2PFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets

class P2PSocketClient(
    val peerDeviceId: String,
    private val onMessageReceived: (ChatMessage) -> Unit,
    private val onAckReceived: (String) -> Unit,
    private val onConnectionStateChanged: (Boolean) -> Unit
) {
    private val TAG = "P2PSocketClient[$peerDeviceId]"
    private var socket: Socket? = null
    private var inStream: BufferedInputStream? = null
    private var outStream: BufferedOutputStream? = null

    private var readerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    @Synchronized
    fun connect(host: String, port: Int = NetworkUtils.DEFAULT_P2P_PORT): Boolean {
        if (socket?.isConnected == true && !socket!!.isClosed) {
            Logger.info(TAG, "Already connected to $host:$port")
            return true
        }

        disconnect()

        return try {
            Logger.info(TAG, "Connecting to $host:$port...")
            val newSocket = Socket()
            newSocket.tcpNoDelay = true
            newSocket.keepAlive = true
            newSocket.connect(InetSocketAddress(host, port), 5000)

            socket = newSocket
            inStream = BufferedInputStream(newSocket.getInputStream())
            outStream = BufferedOutputStream(newSocket.getOutputStream())

            _isConnected.value = true
            onConnectionStateChanged(true)
            Logger.info(TAG, "Successfully connected to $host:$port")

            startReader()
            true
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to connect to $host:$port: ${e.message}")
            disconnect()
            false
        }
    }

    private fun startReader() {
        readerJob?.cancel()
        readerJob = scope.launch {
            val stream = inStream ?: return@launch
            val out = outStream
            val targetSocket = socket ?: return@launch

            try {
                while (isActive && !targetSocket.isClosed && targetSocket.isConnected) {
                    val frame = P2PFrame.readFrom(stream) ?: break
                    when (frame.type) {
                        FrameType.CHAT_MESSAGE -> {
                            val payloadStr = String(frame.payload, StandardCharsets.UTF_8)
                            Logger.info(TAG, "Received incoming CHAT_MESSAGE frame: $payloadStr")
                            try {
                                val json = JSONObject(payloadStr)
                                val message = ChatMessage.fromJson(json)
                                if (message != null) {
                                    onMessageReceived(message)

                                    // Reply with ACK frame
                                    val ackFrame = P2PFrame.createMessageAck(message.messageId)
                                    synchronized(this@P2PSocketClient) {
                                        out?.write(ackFrame.encode())
                                        out?.flush()
                                    }
                                    Logger.info(TAG, "Sent ACK back for message: ${message.messageId}")
                                }
                            } catch (e: Exception) {
                                Logger.error(TAG, "Failed to parse incoming message JSON: ${e.message}", e)
                            }
                        }

                        FrameType.MESSAGE_ACK -> {
                            val payloadStr = String(frame.payload, StandardCharsets.UTF_8)
                            Logger.info(TAG, "Received MESSAGE_ACK frame: $payloadStr")
                            try {
                                val json = JSONObject(payloadStr)
                                val msgId = json.optString("message_id")
                                if (msgId.isNotEmpty()) {
                                    onAckReceived(msgId)
                                }
                            } catch (e: Exception) {
                                Logger.error(TAG, "Failed to parse MESSAGE_ACK JSON: ${e.message}", e)
                            }
                        }

                        FrameType.HEARTBEAT -> {
                            Logger.info(TAG, "Received HEARTBEAT frame from peer")
                        }

                        else -> {
                            Logger.info(TAG, "Received frame of type ${frame.type} (${frame.payload.size} bytes)")
                        }
                    }
                }
            } catch (_: EOFException) {
                Logger.info(TAG, "Peer closed TCP connection")
            } catch (e: SocketException) {
                Logger.info(TAG, "Socket read exception: ${e.message}")
            } catch (e: Exception) {
                Logger.error(TAG, "Reader exception: ${e.message}", e)
            } finally {
                disconnect()
            }
        }
    }

    @Synchronized
    fun sendFrame(frame: P2PFrame): Boolean {
        val out = outStream
        val sock = socket
        if (sock == null || !sock.isConnected || sock.isClosed || out == null) {
            Logger.warning(TAG, "Cannot send frame - socket not connected")
            return false
        }

        return try {
            val encoded = frame.encode()
            out.write(encoded)
            out.flush()
            true
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to send frame: ${e.message}", e)
            disconnect()
            false
        }
    }

    @Synchronized
    fun disconnect() {
        if (_isConnected.value || socket != null) {
            Logger.info(TAG, "Disconnecting P2P socket")
            _isConnected.value = false
            onConnectionStateChanged(false)

            readerJob?.cancel()
            readerJob = null

            try {
                inStream?.close()
            } catch (_: Exception) {}
            inStream = null

            try {
                outStream?.close()
            } catch (_: Exception) {}
            outStream = null

            try {
                socket?.close()
            } catch (_: Exception) {}
            socket = null
        }
    }
}
