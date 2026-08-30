package com.meckchat.android.core

import java.util.UUID

class AppConfig(
    var deviceId: String = "mc_" + UUID.randomUUID().toString(),
    var displayName: String = "Android Device",
    val platform: String = "android",
    val mqttBrokerHost: String = "broker.hivemq.com",
    val mqttBrokerPort: Int = 8883,
    var virtualIp: String = "10.77.0.3"
) {
    companion object {
        val instance = AppConfig()
    }
}
