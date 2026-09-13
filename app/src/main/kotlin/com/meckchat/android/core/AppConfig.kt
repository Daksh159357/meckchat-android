package com.meckchat.android.core

import android.content.Context
import android.os.Build
import java.util.UUID

class AppConfig(
    var deviceId: String = "mc_" + UUID.randomUUID().toString(),
    var displayName: String = "Android Device",
    val platform: String = "android",
    val mqttBrokerHost: String = "broker.hivemq.com",
    val mqttBrokerPort: Int = 8883,
    var virtualIp: String = "10.77.0.3"
) {
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var savedDeviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (savedDeviceId.isNullOrBlank()) {
            savedDeviceId = "mc_" + UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, savedDeviceId).apply()
        }
        deviceId = savedDeviceId

        val savedDisplayName = prefs.getString(KEY_DISPLAY_NAME, null)
        if (!savedDisplayName.isNullOrBlank()) {
            displayName = savedDisplayName
        } else {
            val modelName = Build.MODEL
            if (!modelName.isNullOrBlank()) {
                displayName = modelName
                prefs.edit().putString(KEY_DISPLAY_NAME, modelName).apply()
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "meckchat_config"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DISPLAY_NAME = "display_name"

        val instance = AppConfig()
    }
}

