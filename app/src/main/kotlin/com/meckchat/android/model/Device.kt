package com.meckchat.android.model

import org.json.JSONObject

data class Device(
    val deviceId: String,
    var displayName: String,
    val platform: String,
    var isOnline: Boolean = true,
    var lastSeen: Long = System.currentTimeMillis() / 1000,
    var virtualIp: String? = null
) {
    fun toPresenceOnlineJson(): JSONObject {
        return JSONObject().apply {
            put("type", "presence_online")
            put("protocol_version", 1)
            put("device_id", deviceId)
            put("display_name", displayName)
            put("platform", platform.lowercase())
            put("timestamp", System.currentTimeMillis() / 1000)
        }
    }

    fun toPresenceOfflineJson(): JSONObject {
        return JSONObject().apply {
            put("type", "presence_offline")
            put("device_id", deviceId)
        }
    }

    companion object {
        fun fromPresenceJson(json: JSONObject): Device? {
            val id = json.optString("device_id")
            if (id.isEmpty()) return null

            val type = json.optString("type", "presence_online")
            return Device(
                deviceId = id,
                displayName = json.optString("display_name", "Unknown Device"),
                platform = json.optString("platform", "unknown"),
                isOnline = (type == "presence_online"),
                lastSeen = json.optLong("timestamp", System.currentTimeMillis() / 1000)
            )
        }
    }
}
