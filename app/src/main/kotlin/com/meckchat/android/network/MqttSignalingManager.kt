package com.meckchat.android.network

import com.meckchat.android.core.AppConfig
import com.meckchat.android.core.Logger
import com.meckchat.android.model.Device
import com.meckchat.android.model.DiscoveryRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

class IPv4SSLSocket(
    private val delegate: SSLSocket,
    private val expectedHost: String = "broker.hivemq.com"
) : SSLSocket() {
    override fun connect(endpoint: java.net.SocketAddress?, timeout: Int) {
        val targetEndpoint = if (endpoint is java.net.InetSocketAddress) {
            val host = endpoint.hostString ?: endpoint.hostName ?: expectedHost
            val ipv4 = try {
                val addrs = InetAddress.getAllByName(host)
                addrs.firstOrNull { it is Inet4Address } ?: addrs.firstOrNull() ?: InetAddress.getByName(host)
            } catch (_: Exception) {
                endpoint.address ?: InetAddress.getByName(host)
            }
            java.net.InetSocketAddress(ipv4, endpoint.port)
        } else {
            endpoint
        }
        try {
            val params = delegate.sslParameters
            params.serverNames = listOf(SNIHostName(expectedHost))
            delegate.sslParameters = params
        } catch (_: Throwable) {}
        delegate.connect(targetEndpoint, timeout)
    }

    override fun startHandshake() = delegate.startHandshake()
    override fun getInputStream() = delegate.inputStream
    override fun getOutputStream() = delegate.outputStream
    override fun isConnected(): Boolean = delegate.isConnected
    override fun isClosed(): Boolean = delegate.isClosed
    override fun isBound(): Boolean = delegate.isBound
    override fun close() = delegate.close()
    override fun getSession(): javax.net.ssl.SSLSession = delegate.session
    override fun getSSLParameters(): javax.net.ssl.SSLParameters = delegate.sslParameters
    override fun setSSLParameters(params: javax.net.ssl.SSLParameters?) { delegate.sslParameters = params }
    override fun getEnabledCipherSuites(): Array<String> = delegate.enabledCipherSuites
    override fun setEnabledCipherSuites(suites: Array<out String>?) { delegate.enabledCipherSuites = suites }
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
    override fun getSupportedProtocols(): Array<String> = delegate.supportedProtocols
    override fun getEnabledProtocols(): Array<String> = delegate.enabledProtocols
    override fun setEnabledProtocols(protocols: Array<out String>?) { delegate.enabledProtocols = protocols }
    override fun getNeedClientAuth(): Boolean = delegate.needClientAuth
    override fun setNeedClientAuth(need: Boolean) { delegate.needClientAuth = need }
    override fun getWantClientAuth(): Boolean = delegate.wantClientAuth
    override fun setWantClientAuth(want: Boolean) { delegate.wantClientAuth = want }
    override fun getUseClientMode(): Boolean = delegate.useClientMode
    override fun setUseClientMode(mode: Boolean) { delegate.useClientMode = mode }
    override fun getEnableSessionCreation(): Boolean = delegate.enableSessionCreation
    override fun setEnableSessionCreation(flag: Boolean) { delegate.enableSessionCreation = flag }
    override fun addHandshakeCompletedListener(listener: javax.net.ssl.HandshakeCompletedListener?) {
        delegate.addHandshakeCompletedListener(listener)
    }
    override fun removeHandshakeCompletedListener(listener: javax.net.ssl.HandshakeCompletedListener?) {
        delegate.removeHandshakeCompletedListener(listener)
    }
    override fun getInetAddress(): InetAddress? = delegate.inetAddress
    override fun getPort(): Int = delegate.port
    override fun getLocalSocketAddress(): java.net.SocketAddress? = delegate.localSocketAddress
    override fun getRemoteSocketAddress(): java.net.SocketAddress? = delegate.remoteSocketAddress
    override fun setSoTimeout(timeout: Int) { delegate.soTimeout = timeout }
    override fun getSoTimeout(): Int = delegate.soTimeout
    override fun setTcpNoDelay(on: Boolean) { delegate.tcpNoDelay = on }
    override fun getTcpNoDelay(): Boolean = delegate.tcpNoDelay
    override fun setKeepAlive(on: Boolean) { delegate.keepAlive = on }
    override fun getKeepAlive(): Boolean = delegate.keepAlive
}

class IPv4SSLSocketFactory(
    private val expectedHost: String = "broker.hivemq.com"
) : SSLSocketFactory() {
    private val delegate: SSLSocketFactory

    init {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, null, null)
        delegate = sslContext.socketFactory
    }

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(): Socket {
        val sslSocket = delegate.createSocket() as SSLSocket
        return IPv4SSLSocket(sslSocket, expectedHost)
    }

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        val targetHost = if (host.isNotEmpty()) host else expectedHost
        val sslSocket = delegate.createSocket(s, targetHost, port, autoClose) as SSLSocket
        return IPv4SSLSocket(sslSocket, targetHost)
    }

    override fun createSocket(host: String, port: Int): Socket {
        val targetHost = if (host.isNotEmpty()) host else expectedHost
        val sslSocket = delegate.createSocket() as SSLSocket
        val socket = IPv4SSLSocket(sslSocket, targetHost)
        socket.connect(java.net.InetSocketAddress(targetHost, port), 30000)
        return socket
    }

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
        val targetHost = if (host.isNotEmpty()) host else expectedHost
        val sslSocket = delegate.createSocket() as SSLSocket
        sslSocket.bind(java.net.InetSocketAddress(localHost, localPort))
        val socket = IPv4SSLSocket(sslSocket, targetHost)
        socket.connect(java.net.InetSocketAddress(targetHost, port), 30000)
        return socket
    }

    override fun createSocket(host: InetAddress, port: Int): Socket {
        val sslSocket = delegate.createSocket() as SSLSocket
        val socket = IPv4SSLSocket(sslSocket, expectedHost)
        socket.connect(java.net.InetSocketAddress(host, port), 30000)
        return socket
    }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
        val sslSocket = delegate.createSocket() as SSLSocket
        sslSocket.bind(java.net.InetSocketAddress(localAddress, localPort))
        val socket = IPv4SSLSocket(sslSocket, expectedHost)
        socket.connect(java.net.InetSocketAddress(address, port), 30000)
        return socket
    }
}

class MqttSignalingManager(
    private val appConfig: AppConfig = AppConfig.instance
) {
    companion object {
        private const val TAG = "MqttSignaling"
        const val TOPIC_DISCOVERY = "meckchat/v1/discovery"
        const val TOPIC_PRESENCE_ONLINE_PREFIX = "meckchat/v1/presence/online/"
        const val TOPIC_PRESENCE_OFFLINE_PREFIX = "meckchat/v1/presence/offline/"
        const val TOPIC_MESSAGE_PREFIX = "meckchat/v1/msg/"

        val instance = MqttSignalingManager()

        fun getPresenceOnlineTopic(deviceId: String): String = "$TOPIC_PRESENCE_ONLINE_PREFIX$deviceId"
        fun getPresenceOfflineTopic(deviceId: String): String = "$TOPIC_PRESENCE_OFFLINE_PREFIX$deviceId"
        fun getDirectMessageTopic(deviceId: String): String = "$TOPIC_MESSAGE_PREFIX$deviceId"
    }

    private var mqttClient: MqttAsyncClient? = null
    private val persistence = MemoryPersistence()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _discoveredDevicesMap = MutableStateFlow<Map<String, Device>>(emptyMap())
    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices.asStateFlow()

    // Delegate message storage to P2PTransportManager
    val messagesMap: StateFlow<Map<String, List<com.meckchat.android.model.ChatMessage>>>
        get() = P2PTransportManager.instance.messagesMap

    init {
        P2PTransportManager.instance.onRequestRediscovery = {
            broadcastDiscovery()
        }
    }

    fun isConnected(): Boolean {
        return mqttClient?.isConnected == true && _connectionState.value == ConnectionState.CONNECTED
    }

    fun getCurrentDevice(): Device {
        return Device(
            deviceId = appConfig.deviceId,
            displayName = appConfig.displayName,
            platform = appConfig.platform,
            isOnline = true,
            endpoints = NetworkUtils.getLocalEndpoints(NetworkUtils.DEFAULT_P2P_PORT)
        )
    }

    fun connect(
        brokerHost: String = appConfig.mqttBrokerHost,
        port: Int = appConfig.mqttBrokerPort,
        device: Device = getCurrentDevice()
    ) {
        if (mqttClient != null && mqttClient!!.isConnected && _connectionState.value == ConnectionState.CONNECTED) {
            Logger.info(TAG, "MQTT client is already connected.")
            return
        }

        if (_connectionState.value == ConnectionState.CONNECTING) {
            Logger.info(TAG, "MQTT client is already connecting...")
            return
        }

        try {
            System.setProperty("java.net.preferIPv4Addresses", "true")
            System.setProperty("java.net.preferIPv4Stack", "true")
        } catch (_: Exception) {}

        _connectionState.value = ConnectionState.CONNECTING
        _errorMessage.value = null
        Logger.info(TAG, "MQTT initializing")
        Logger.info(TAG, "MQTT connecting to $brokerHost:$port")

        val serverUri = "ssl://$brokerHost:$port"
        val clientId = "${device.deviceId}_${System.currentTimeMillis() % 100000}"

        try {
            synchronized(this) {
                mqttClient?.let {
                    try {
                        it.disconnectForcibly(1000, 1000, false)
                        it.close()
                    } catch (_: Exception) {}
                }

                mqttClient = MqttAsyncClient(serverUri, clientId, persistence)
            }

            mqttClient?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Logger.info(TAG, "MQTT connected (connectComplete, reconnect=$reconnect)")
                    _connectionState.value = ConnectionState.CONNECTED
                    _errorMessage.value = null
                    subscribeToTopics()
                    publishPresence(device)
                }

                override fun connectionLost(cause: Throwable?) {
                    val causeMsg = cause?.localizedMessage ?: cause?.message ?: "Connection lost"
                    _connectionState.value = ConnectionState.RECONNECTING
                    _errorMessage.value = causeMsg
                    Logger.warning(TAG, "MQTT reconnecting ($causeMsg)")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic == null || message == null) return
                    val payload = String(message.payload, StandardCharsets.UTF_8)
                    handleIncomingMessage(topic, payload)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
                connectionTimeout = 30
                keepAliveInterval = 60
                socketFactory = IPv4SSLSocketFactory(expectedHost = brokerHost)
                sslHostnameVerifier = HostnameVerifier { _, session ->
                    HttpsURLConnection.getDefaultHostnameVerifier().verify(brokerHost, session)
                }

                val lwtPayload = device.toPresenceOfflineString().toByteArray(StandardCharsets.UTF_8)
                setWill(getPresenceOfflineTopic(device.deviceId), lwtPayload, 1, true)
            }

            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Logger.info(TAG, "MQTT connected (initial connect onSuccess)")
                    if (_connectionState.value != ConnectionState.CONNECTED) {
                        _connectionState.value = ConnectionState.CONNECTED
                        _errorMessage.value = null
                        subscribeToTopics()
                        publishPresence(device)
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    val errorMsg = exception?.localizedMessage ?: exception?.message ?: "Connection failed"
                    _connectionState.value = ConnectionState.ERROR
                    _errorMessage.value = errorMsg
                    Logger.error(TAG, "MQTT connection error: $errorMsg", exception)
                }
            })

        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: e.message ?: "Failed to initialize MQTT"
            _connectionState.value = ConnectionState.ERROR
            _errorMessage.value = errorMsg
            Logger.error(TAG, "MQTT connection error: $errorMsg", e)
        }
    }

    private fun subscribeToTopics() {
        val client = mqttClient ?: return
        try {
            Logger.info(TAG, "MQTT subscribing to discovery and presence topics (strictly bootstrap)")
            val topics = arrayOf(
                TOPIC_DISCOVERY,
                "$TOPIC_PRESENCE_ONLINE_PREFIX+",
                "$TOPIC_PRESENCE_OFFLINE_PREFIX+"
            )
            val qos = intArrayOf(1, 1, 1)

            client.subscribe(topics, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Logger.info(TAG, "MQTT subscription successful - broadcasting discovery")
                    broadcastDiscovery()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Logger.error(TAG, "MQTT subscription failed: ${exception?.message}")
                }
            })
        } catch (e: Exception) {
            Logger.error(TAG, "Exception during MQTT subscription: ${e.message}", e)
        }
    }

    fun publishPresence(device: Device = getCurrentDevice()) {
        val client = mqttClient ?: return
        try {
            val payload = device.toPresenceOnlineString().toByteArray(StandardCharsets.UTF_8)
            val onlineTopic = getPresenceOnlineTopic(device.deviceId)

            val msg1 = MqttMessage(payload).apply {
                qos = 1
                isRetained = true
            }
            client.publish(onlineTopic, msg1)
            Logger.info(TAG, "MQTT online presence published (retained)")

            // Also broadcast discovery with full TCP endpoints
            broadcastDiscovery(device)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to publish online presence: ${e.message}", e)
        }
    }

    fun broadcastDiscovery(device: Device = getCurrentDevice()) {
        val client = mqttClient ?: return
        try {
            val discoveryPayload = device.toDiscoveryString().toByteArray(StandardCharsets.UTF_8)
            val msg = MqttMessage(discoveryPayload).apply {
                qos = 1
                isRetained = false
            }
            client.publish(TOPIC_DISCOVERY, msg)
            Logger.info(TAG, "MQTT discovery broadcast sent with ${device.endpoints.size} endpoints")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to broadcast discovery: ${e.message}", e)
        }
    }

    fun handleIncomingMessage(topic: String, payload: String) {
        Logger.info(TAG, "MQTT message received on topic: $topic")
        try {
            val json = JSONObject(payload)

            val type = json.optString("type")
            val senderDeviceId = json.optString("device_id")
            val myDeviceId = appConfig.deviceId

            if (senderDeviceId.isEmpty() || senderDeviceId == myDeviceId) {
                return
            }

            // Only filter out ephemeral CI test workers
            if (senderDeviceId.startsWith("mc_test_ci_") || senderDeviceId.contains("ci_worker")) {
                Logger.info(TAG, "Ignoring automated CI worker device: $senderDeviceId")
                return
            }

            when (type) {
                "presence_online" -> {
                    val discovered = Device.fromPresenceJson(json)
                    if (discovered != null) {
                        updateDiscoveredDevice(discovered)
                        P2PTransportManager.instance.onPeerDiscovered(discovered)
                        Logger.info(TAG, "Device discovered: ${discovered.deviceId}")
                    }
                }
                "presence_offline" -> {
                    markDeviceOffline(senderDeviceId)
                    P2PTransportManager.instance.onPeerOffline(senderDeviceId)
                    Logger.info(TAG, "Device offline: $senderDeviceId")
                }
                "discovery_request" -> {
                    Logger.info(TAG, "Discovery request from: $senderDeviceId")
                    broadcastDiscovery(getCurrentDevice())
                }
                else -> {
                    val discovered = Device.fromPresenceJson(json)
                    if (discovered != null) {
                        updateDiscoveredDevice(discovered)
                        P2PTransportManager.instance.onPeerDiscovered(discovered)
                        Logger.info(TAG, "Device discovered: ${discovered.deviceId}")
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to parse incoming message: ${e.message}")
        }
    }

    fun sendChatMessage(recipientDeviceId: String, content: String): com.meckchat.android.model.ChatMessage {
        return P2PTransportManager.instance.sendMessage(recipientDeviceId, content)
    }

    private fun updateDiscoveredDevice(device: Device) {
        _discoveredDevicesMap.update { current ->
            val existing = current[device.deviceId]
            val updated = if (existing != null) {
                existing.copy(
                    displayName = if (device.displayName.isNotEmpty() && device.displayName != device.deviceId) device.displayName else existing.displayName,
                    platform = if (device.platform.isNotEmpty()) device.platform else existing.platform,
                    isOnline = true,
                    lastSeen = device.lastSeen,
                    endpoints = if (device.endpoints.isNotEmpty()) device.endpoints else existing.endpoints
                )
            } else {
                device.copy(isOnline = true)
            }
            current + (device.deviceId to updated)
        }
        _discoveredDevices.value = _discoveredDevicesMap.value.values.toList()
    }

    private fun markDeviceOffline(deviceId: String) {
        _discoveredDevicesMap.update { current ->
            val existing = current[deviceId]
            if (existing != null) {
                current + (deviceId to existing.copy(isOnline = false))
            } else {
                current
            }
        }
        _discoveredDevices.value = _discoveredDevicesMap.value.values.toList()
    }

    fun removeDevice(deviceId: String) {
        _discoveredDevicesMap.update { current ->
            current - deviceId
        }
        _discoveredDevices.value = _discoveredDevicesMap.value.values.toList()
        Logger.info(TAG, "Removed device: $deviceId")
    }

    fun clearOfflineDevices() {
        _discoveredDevicesMap.update { current ->
            current.filterValues { it.isOnline }
        }
        _discoveredDevices.value = _discoveredDevicesMap.value.values.toList()
        Logger.info(TAG, "Cleared all offline devices")
    }

    @Synchronized
    fun disconnect() {
        val client = mqttClient
        if (client != null && client.isConnected) {
            try {
                val myDevice = getCurrentDevice()
                val offlinePayload = myDevice.toPresenceOfflineString().toByteArray(StandardCharsets.UTF_8)
                val msg = MqttMessage(offlinePayload).apply { qos = 1 }
                val offlineTopic = getPresenceOfflineTopic(myDevice.deviceId)

                try {
                    client.publish(offlineTopic, msg)
                    client.publish(TOPIC_DISCOVERY, msg)
                } catch (_: Exception) {}

                client.disconnect()
            } catch (e: Exception) {
                Logger.error(TAG, "Error disconnecting MQTT client: ${e.message}", e)
            }
        }
        _connectionState.value = ConnectionState.DISCONNECTED
        _errorMessage.value = null
        Logger.info(TAG, "MQTT disconnected")
    }
}
