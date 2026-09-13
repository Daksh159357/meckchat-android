package com.meckchat.android.network

import com.meckchat.android.core.Logger
import com.meckchat.android.model.ChatMessage
import com.meckchat.android.model.FrameType
import com.meckchat.android.model.MessageAck
import com.meckchat.android.model.P2PFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets

class P2PSocketServer(
    private val port: Int = NetworkUtils.DEFAULT_P2P_PORT,
    private val onMessageReceived: (ChatMessage) -> Unit,
    private val onAckReceived: (String) -> Unit
) {
    private val TAG = "P2PSocketServer"
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val activeClients = mutableListOf<Socket>()

    @Synchronized
    fun start() {
        if (serverSocket != null && !serverSocket!!.isClosed) {
            Logger.info(TAG, "Server already running on port $port")
            return
        }

        try {
            serverSocket = ServerSocket(port).apply {
                reuseAddress = true
            }
            Logger.info(TAG, "P2P TCP Server listening on port $port")

            serverJob = scope.launch {
                while (isActive && serverSocket?.isClosed == false) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        Logger.info(TAG, "Accepted incoming P2P connection from ${clientSocket.remoteSocketAddress}")
                        synchronized(activeClients) {
                            activeClients.add(clientSocket)
                        }
                        launch {
                            handleClient(clientSocket)
                        }
                    } catch (e: SocketException) {
                        if (isActive && serverSocket?.isClosed == false) {
                            Logger.warning(TAG, "Socket exception in server accept loop: ${e.message}")
                        }
                        break
                    } catch (e: Exception) {
                        Logger.error(TAG, "Error accepting client connection: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to start P2P TCP Server on port $port: ${e.message}", e)
        }
    }

    private fun handleClient(socket: Socket) {
        val remoteAddr = socket.remoteSocketAddress.toString()
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 0 // Wait for incoming frames indefinitely
            val inStream = BufferedInputStream(socket.getInputStream())
            val outStream = BufferedOutputStream(socket.getOutputStream())

            while (!socket.isClosed && socket.isConnected) {
                val frame = P2PFrame.readFrom(inStream) ?: break
                when (frame.type) {
                    FrameType.CHAT_MESSAGE -> {
                        val payloadStr = String(frame.payload, StandardCharsets.UTF_8)
                        Logger.info(TAG, "Received CHAT_MESSAGE frame from $remoteAddr: $payloadStr")
                        try {
                            val json = JSONObject(payloadStr)
                            val message = ChatMessage.fromJson(json)
                            if (message != null) {
                                onMessageReceived(message)

                                // Auto-reply with MESSAGE_ACK back to the sender
                                val ackFrame = P2PFrame.createMessageAck(message.messageId)
                                outStream.write(ackFrame.encode())
                                outStream.flush()
                                Logger.info(TAG, "Sent MESSAGE_ACK back to $remoteAddr for messageId: ${message.messageId}")
                            }
                        } catch (e: Exception) {
                            Logger.error(TAG, "Failed to parse incoming chat message JSON: ${e.message}", e)
                        }
                    }

                    FrameType.MESSAGE_ACK -> {
                        val payloadStr = String(frame.payload, StandardCharsets.UTF_8)
                        Logger.info(TAG, "Received MESSAGE_ACK frame from $remoteAddr: $payloadStr")
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
                        Logger.info(TAG, "Received HEARTBEAT frame from $remoteAddr")
                        val heartbeatAck = P2PFrame(FrameType.HEARTBEAT, byteArrayOf())
                        outStream.write(heartbeatAck.encode())
                        outStream.flush()
                    }

                    else -> {
                        Logger.info(TAG, "Received frame of type ${frame.type} from $remoteAddr (${frame.payload.size} bytes)")
                    }
                }
            }
        } catch (_: EOFException) {
            Logger.info(TAG, "Client $remoteAddr disconnected gracefully")
        } catch (e: SocketException) {
            Logger.info(TAG, "Client socket $remoteAddr closed: ${e.message}")
        } catch (e: Exception) {
            Logger.error(TAG, "Error handling client $remoteAddr: ${e.message}", e)
        } finally {
            synchronized(activeClients) {
                activeClients.remove(socket)
            }
            try {
                socket.close()
            } catch (_: Exception) {}
            Logger.info(TAG, "Closed client connection for $remoteAddr")
        }
    }

    @Synchronized
    fun stop() {
        Logger.info(TAG, "Stopping P2P TCP Server on port $port")
        serverJob?.cancel()
        serverJob = null

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        synchronized(activeClients) {
            for (client in activeClients) {
                try {
                    client.close()
                } catch (_: Exception) {}
            }
            activeClients.clear()
        }
    }
}
