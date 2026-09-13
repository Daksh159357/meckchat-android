package com.meckchat.android.network

import com.meckchat.android.core.AppConfig
import com.meckchat.android.core.Logger
import com.meckchat.android.model.ChatMessage
import com.meckchat.android.model.Device
import com.meckchat.android.model.P2PConnectionState
import com.meckchat.android.model.P2PFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class P2PTransportManager(
    private val appConfig: AppConfig = AppConfig.instance
) {
    private val TAG = "P2PTransportManager"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _messagesMap = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val messagesMap: StateFlow<Map<String, List<ChatMessage>>> = _messagesMap.asStateFlow()

    private val _peerStates = MutableStateFlow<Map<String, P2PConnectionState>>(emptyMap())
    val peerStates: StateFlow<Map<String, P2PConnectionState>> = _peerStates.asStateFlow()

    private val clientPool = mutableMapOf<String, P2PSocketClient>()
    private val knownPeerEndpoints = mutableMapOf<String, List<com.meckchat.android.model.Endpoint>>()

    private var server: P2PSocketServer? = null

    // Callback to request MQTT signaling re-discovery if P2P endpoints are unreachable
    var onRequestRediscovery: (() -> Unit)? = null

    init {
        startServer()
    }

    @Synchronized
    fun startServer(port: Int = NetworkUtils.DEFAULT_P2P_PORT) {
        if (server == null) {
            Logger.info(TAG, "Initializing P2PSocketServer on port $port")
            server = P2PSocketServer(
                port = port,
                onMessageReceived = { message -> handleIncomingMessage(message) },
                onAckReceived = { messageId -> handleAckReceived(messageId) }
            )
            server?.start()
        }
    }

    fun onPeerDiscovered(device: Device) {
        if (device.deviceId == appConfig.deviceId) return
        Logger.info(TAG, "Peer discovered: ${device.deviceId} with ${device.endpoints.size} endpoints")

        synchronized(knownPeerEndpoints) {
            knownPeerEndpoints[device.deviceId] = device.endpoints
        }

        if (device.endpoints.isNotEmpty()) {
            val currentState = _peerStates.value[device.deviceId]
            if (currentState != P2PConnectionState.P2P_CONNECTED && currentState != P2PConnectionState.P2P_CONNECTING) {
                connectToPeer(device.deviceId, device.endpoints)
            }
        }
    }

    fun connectToPeer(peerDeviceId: String, endpoints: List<com.meckchat.android.model.Endpoint> = emptyList()) {
        val targetEndpoints = if (endpoints.isNotEmpty()) {
            endpoints
        } else {
            synchronized(knownPeerEndpoints) { knownPeerEndpoints[peerDeviceId] ?: emptyList() }
        }

        if (targetEndpoints.isEmpty()) {
            Logger.warning(TAG, "Cannot connect to $peerDeviceId - no endpoints available. Triggering rediscovery.")
            _peerStates.update { it + (peerDeviceId to P2PConnectionState.REDISCOVERING) }
            onRequestRediscovery?.invoke()
            return
        }

        scope.launch {
            _peerStates.update { it + (peerDeviceId to P2PConnectionState.P2P_CONNECTING) }
            val client = getOrCreateClient(peerDeviceId)

            var connected = false
            for (ep in targetEndpoints) {
                Logger.info(TAG, "Attempting direct TCP connection to $peerDeviceId at ${ep.host}:${ep.port}")
                if (client.connect(ep.host, ep.port)) {
                    connected = true
                    _peerStates.update { it + (peerDeviceId to P2PConnectionState.P2P_CONNECTED) }
                    Logger.info(TAG, "P2P TCP connection ESTABLISHED with $peerDeviceId at ${ep.host}:${ep.port}")
                    break
                }
            }

            if (!connected) {
                Logger.warning(TAG, "Failed to connect to any endpoint for $peerDeviceId")
                _peerStates.update { it + (peerDeviceId to P2PConnectionState.P2P_DISCONNECTED) }
            }
        }
    }

    @Synchronized
    private fun getOrCreateClient(peerDeviceId: String): P2PSocketClient {
        return clientPool.getOrPut(peerDeviceId) {
            P2PSocketClient(
                peerDeviceId = peerDeviceId,
                onMessageReceived = { message -> handleIncomingMessage(message) },
                onAckReceived = { messageId -> handleAckReceived(messageId) },
                onConnectionStateChanged = { isConnected ->
                    _peerStates.update {
                        val newState = if (isConnected) P2PConnectionState.P2P_CONNECTED else P2PConnectionState.P2P_DISCONNECTED
                        it + (peerDeviceId to newState)
                    }
                }
            )
        }
    }

    fun sendMessage(recipientDeviceId: String, content: String): ChatMessage {
        val msgId = "msg_${UUID.randomUUID()}"
        val message = ChatMessage(
            messageId = msgId,
            senderDeviceId = appConfig.deviceId,
            recipientDeviceId = recipientDeviceId,
            content = content,
            timestamp = System.currentTimeMillis() / 1000,
            isSent = true,
            isDelivered = false
        )

        // Optimistically add message to state
        _messagesMap.update { current ->
            val existing = current[recipientDeviceId] ?: emptyList()
            current + (recipientDeviceId to (existing + message))
        }

        scope.launch {
            val client = getOrCreateClient(recipientDeviceId)
            val isConn = client.isConnected.value

            if (!isConn) {
                Logger.info(TAG, "P2P socket not connected to $recipientDeviceId - attempting connection before send")
                val endpoints = synchronized(knownPeerEndpoints) { knownPeerEndpoints[recipientDeviceId] ?: emptyList() }
                for (ep in endpoints) {
                    if (client.connect(ep.host, ep.port)) break
                }
            }

            val frame = P2PFrame.createChatMessage(message)
            val success = client.sendFrame(frame)
            if (success) {
                Logger.info(TAG, "Sent CHAT_MESSAGE frame over TCP to $recipientDeviceId: $content")
            } else {
                Logger.warning(TAG, "Failed to send CHAT_MESSAGE frame over TCP to $recipientDeviceId")
                _messagesMap.update { current ->
                    val existing = current[recipientDeviceId] ?: emptyList()
                    val updated = existing.map {
                        if (it.messageId == msgId) it.copy(isFailed = true) else it
                    }
                    current + (recipientDeviceId to updated)
                }
            }
        }

        return message
    }

    private fun handleIncomingMessage(message: ChatMessage) {
        if (message.senderDeviceId == appConfig.deviceId) return
        val peerId = message.senderDeviceId
        Logger.info(TAG, "Dispatching incoming chat message from $peerId: ${message.content}")

        _messagesMap.update { current ->
            val existing = current[peerId] ?: emptyList()
            // Deduplicate by messageId
            if (existing.any { it.messageId == message.messageId }) {
                current
            } else {
                current + (peerId to (existing + message.copy(isDelivered = true)))
            }
        }
    }

    private fun handleAckReceived(messageId: String) {
        Logger.info(TAG, "Processing ACK for messageId: $messageId")
        _messagesMap.update { current ->
            current.mapValues { (_, list) ->
                list.map { msg ->
                    if (msg.messageId == messageId) {
                        msg.copy(isDelivered = true, isFailed = false)
                    } else {
                        msg
                    }
                }
            }
        }
    }

    fun onPeerOffline(peerDeviceId: String) {
        Logger.info(TAG, "Peer offline notification: $peerDeviceId")
        clientPool[peerDeviceId]?.disconnect()
        _peerStates.update { it + (peerDeviceId to P2PConnectionState.P2P_DISCONNECTED) }
    }

    @Synchronized
    fun stop() {
        Logger.info(TAG, "Shutting down P2PTransportManager")
        server?.stop()
        server = null
        for ((_, client) in clientPool) {
            client.disconnect()
        }
        clientPool.clear()
    }

    companion object {
        val instance = P2PTransportManager()
    }
}
