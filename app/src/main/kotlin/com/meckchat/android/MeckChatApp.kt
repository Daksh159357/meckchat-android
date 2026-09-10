package com.meckchat.android

import android.app.Application
import com.meckchat.android.core.Logger
import com.meckchat.android.network.MqttSignalingManager

class MeckChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            System.setProperty("java.net.preferIPv4Addresses", "true")
            System.setProperty("java.net.preferIPv4Stack", "true")
        } catch (_: Exception) {}

        Logger.info("MeckChatApp", "Application onCreate - initializing MQTT signaling")
        MqttSignalingManager.instance.connect()
    }
}
