package com.meckchat.android

import android.app.Application
import com.meckchat.android.core.Logger
import com.meckchat.android.network.MqttSignalingManager

class MeckChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.info("MeckChatApp", "Application onCreate - initializing MQTT signaling")
        MqttSignalingManager.instance.connect()
    }
}
