package com.meckchat.android.data.remote

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.meckchat.android.core.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Service responsible for WebRTC signaling over free public HiveMQ MQTT broker.
 * Exchanges SDP offers, answers, and ICE candidates using topic: meckchat/webrtc/<keyword>
 */
class MqttSignalingService {

    companion object {
        private const val TAG = "MqttSignalingService"
        private const val BROKER_HOST = "broker.hivemq.com"
        private const val BROKER_PORT = 1883
        private const val TOPIC_PREFIX = "meckchat/webrtc/"
    }

    val myClientId: String = "mc_webrtc_" + UUID.randomUUID().toString()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    private var client: Mqtt3AsyncClient? = null
    private var currentTopic: String? = null

    private val _incomingSignals = MutableSharedFlow<Map<String, Any>>(extraBufferCapacity = 64)
    val incomingSignals: SharedFlow<Map<String, Any>> = _incomingSignals.asSharedFlow()

    private val typeToken = object : TypeToken<Map<String, Any>>() {}.type

    /**
     * Connects to the public HiveMQ MQTT broker and subscribes to the sanitized room keyword.
     */
    fun connectAndSubscribe(keyword: String, onConnected: (() -> Unit)? = null, onError: ((Throwable) -> Unit)? = null) {
        val sanitizedKeyword = keyword.trim().lowercase().replace("\\s+".toRegex(), "-")
        val topic = "$TOPIC_PREFIX$sanitizedKeyword"
        currentTopic = topic

        Logger.info(TAG, "Connecting to $BROKER_HOST:$BROKER_PORT with clientId: $myClientId")

        val mqttClient = MqttClient.builder()
            .useMqttVersion3()
            .identifier(myClientId)
            .serverHost(BROKER_HOST)
            .serverPort(BROKER_PORT)
            .buildAsync()

        this.client = mqttClient

        mqttClient.connectWith()
            .cleanSession(true)
            .send()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    Logger.error(TAG, "Failed to connect to MQTT broker: ${throwable.message}", throwable)
                    onError?.invoke(throwable)
                } else {
                    Logger.info(TAG, "Connected to MQTT broker. Subscribing to topic: $topic")
                    // Note: HiveMQ Async client auto-reconnect or manual disconnect handling
                    // TODO: Implement automated reconnect logic with exponential backoff for production
                    subscribeToTopic(mqttClient, topic, onConnected, onError)
                }
            }
    }

    private fun subscribeToTopic(
        mqttClient: Mqtt3AsyncClient,
        topic: String,
        onConnected: (() -> Unit)?,
        onError: ((Throwable) -> Unit)?
    ) {
        mqttClient.subscribeWith()
            .topicFilter(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .callback { publish ->
                try {
                    val payloadStr = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
                    val map: Map<String, Any> = gson.fromJson(payloadStr, typeToken)
                    val sender = map["sender"] as? String

                    // Ignore messages sent by ourselves
                    if (sender != null && sender == myClientId) {
                        return@callback
                    }

                    Logger.info(TAG, "Incoming signaling payload from $sender: ${map["type"]}")
                    scope.launch {
                        _incomingSignals.emit(map)
                    }
                } catch (e: Exception) {
                    Logger.error(TAG, "Error parsing incoming signaling message: ${e.message}", e)
                }
            }
            .send()
            .whenComplete { _, subError ->
                if (subError != null) {
                    Logger.error(TAG, "Failed to subscribe to $topic: ${subError.message}", subError)
                    onError?.invoke(subError)
                } else {
                    Logger.info(TAG, "Successfully subscribed to $topic")
                    onConnected?.invoke()
                }
            }
    }

    /**
     * Publishes a signaling map (offer, answer, or ice candidate) attaching the sender ID.
     */
    fun publishSignal(signal: Map<String, Any>) {
        val topic = currentTopic
        val mqttClient = client
        if (topic == null || mqttClient == null) {
            Logger.warning(TAG, "Cannot publish signal: client or topic is null")
            return
        }

        val mutableSignal = HashMap(signal)
        mutableSignal["sender"] = myClientId
        val jsonString = gson.toJson(mutableSignal)

        Logger.info(TAG, "Publishing signal ${signal["type"]} to $topic")

        mqttClient.publishWith()
            .topic(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .payload(jsonString.toByteArray(StandardCharsets.UTF_8))
            .send()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    Logger.error(TAG, "Failed to publish signal: ${throwable.message}", throwable)
                }
            }
    }

    /**
     * Disconnects from MQTT broker and cleans up resources.
     */
    fun disconnect() {
        try {
            Logger.info(TAG, "Disconnecting from MQTT signaling broker")
            client?.disconnect()
            client = null
            currentTopic = null
        } catch (e: Exception) {
            Logger.error(TAG, "Error disconnecting MQTT: ${e.message}", e)
        }
    }
}
