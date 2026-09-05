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
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLContext

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

class MqttSignalingManager(
    private val appConfig: AppConfig = AppConfig.instance
) {
    companion object {
        private const val TAG = "MqttSignaling"
        const val TOPIC_DISCOVERY = "meckchat/v1/discovery"
        const val TOPIC_PRESENCE_ONLINE_PREFIX = "meckchat/v1/presence/online/"
        const val TOPIC_PRESENCE_OFFLINE_PREFIX = "meckchat/v1/presence/offline/"

        val instance = MqttSignalingManager()

        fun getPresenceOnlineTopic(deviceId: String): String = "$TOPIC_PRESENCE_ONLINE_PREFIX$deviceId"
        fun getPresenceOfflineTopic(deviceId: String): String = "$TOPIC_PRESENCE_OFFLINE_PREFIX$deviceId"
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

    fun isConnected(): Boolean {
        return mqttClient?.isConnected == true && _connectionState.value == ConnectionState.CONNECTED
    }

    fun getCurrentDevice(): Device {
        return Device(
            deviceId = appConfig.deviceId,
            displayName = appConfig.displayName,
            platform = appConfig.platform,
            isOnline = true
        )
    }

    @Synchronized
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

        val serverUri = "ssl://$brokerHost:$port"
        val clientId = "${device.deviceId}_${System.currentTimeMillis() % 100000}"

        _connectionState.value = ConnectionState.CONNECTING
        _errorMessage.value = null
        Logger.info(TAG, "MQTT initializing")
        Logger.info(TAG, "MQTT connecting to $brokerHost:$port")

        try {
            mqttClient?.let {
                try {
                    it.disconnectForcibly(1000, 1000, false)
                    it.close()
                } catch (_: Exception) {}
            }

            mqttClient = MqttAsyncClient(serverUri, clientId, persistence)
            mqttClient?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Logger.info(TAG, "MQTT connected (connectComplete, reconnect=$reconnect)")
                    handleSuccessfulConnection(device)
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

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, null, null)

            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
                connectionTimeout = 20
                keepAliveInterval = 45
                socketFactory = sslContext.socketFactory

                val lwtPayload = device.toPresenceOfflineString().toByteArray(StandardCharsets.UTF_8)
                setWill(getPresenceOfflineTopic(device.deviceId), lwtPayload, 1, false)
            }

            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Logger.info(TAG, "MQTT connected (initial connect onSuccess)")
                    handleSuccessfulConnection(device)
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

    @Synchronized
    private fun handleSuccessfulConnection(device: Device) {
        _connectionState.value = ConnectionState.CONNECTED
        _errorMessage.value = null
        Logger.info(TAG, "MQTT connected")

        subscribeToTopics()
        publishPresence(device)
    }

    private fun subscribeToTopics() {
        val client = mqttClient ?: return
        try {
            Logger.info(TAG, "MQTT subscribing")
            val topics = arrayOf(
                TOPIC_DISCOVERY,
                "$TOPIC_PRESENCE_ONLINE_PREFIX+",
                "$TOPIC_PRESENCE_OFFLINE_PREFIX+"
            )
            val qos = intArrayOf(1, 1, 1)

            client.subscribe(topics, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Logger.info(TAG, "MQTT subscription successful")
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

            val msg1 = MqttMessage(payload).apply { qos = 1 }
            client.publish(onlineTopic, msg1)

            val msg2 = MqttMessage(payload).apply { qos = 1 }
            client.publish(TOPIC_DISCOVERY, msg2)

            Logger.info(TAG, "MQTT online presence published")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to publish online presence: ${e.message}", e)
        }
    }

    fun broadcastDiscovery() {
        val client = mqttClient ?: return
        try {
            val myDevice = getCurrentDevice()
            val request = DiscoveryRequest(deviceId = myDevice.deviceId)
            val reqPayload = request.toJson().toString().toByteArray(StandardCharsets.UTF_8)

            val msg = MqttMessage(reqPayload).apply { qos = 1 }
            client.publish(TOPIC_DISCOVERY, msg)
            Logger.info(TAG, "MQTT discovery published")

            publishPresence(myDevice)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to broadcast discovery: ${e.message}", e)
        }
    }

    fun handleIncomingMessage(topic: String, payload: String) {
        Logger.info(TAG, "MQTT discovery message received on topic: $topic")
        try {
            val json = JSONObject(payload)
            val type = json.optString("type")
            val senderDeviceId = json.optString("device_id")
            val myDeviceId = appConfig.deviceId

            if (senderDeviceId.isEmpty() || senderDeviceId == myDeviceId) {
                return
            }

            when (type) {
                "presence_online" -> {
                    val discovered = Device.fromPresenceJson(json)
                    if (discovered != null) {
                        updateDiscoveredDevice(discovered)
                        Logger.info(TAG, "Device discovered: ${discovered.deviceId}")
                    }
                }
                "presence_offline" -> {
                    markDeviceOffline(senderDeviceId)
                    Logger.info(TAG, "Device offline: $senderDeviceId")
                }
                "discovery_request" -> {
                    Logger.info(TAG, "Discovery request from: $senderDeviceId")
                    publishPresence(getCurrentDevice())
                }
                else -> {
                    val discovered = Device.fromPresenceJson(json)
                    if (discovered != null) {
                        updateDiscoveredDevice(discovered)
                        Logger.info(TAG, "Device discovered: ${discovered.deviceId}")
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to parse discovery message: ${e.message}")
        }
    }

    private fun updateDiscoveredDevice(device: Device) {
        _discoveredDevicesMap.update { current ->
            current + (device.deviceId to device)
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
