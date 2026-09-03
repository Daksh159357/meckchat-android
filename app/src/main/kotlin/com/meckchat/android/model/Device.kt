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
        val json = JSONObject()
        json.put("type", "presence_online")
        json.put("protocol_version", 1)
        json.put("device_id", deviceId)
        json.put("display_name", displayName)
        json.put("platform", platform.lowercase())
        json.put("timestamp", System.currentTimeMillis() / 1000)
        return json
    }

    fun toPresenceOnlineString(): String {
        return "{\"type\":\"presence_online\",\"protocol_version\":1,\"device_id\":\"$deviceId\",\"display_name\":\"$displayName\",\"platform\":\"${platform.lowercase()}\",\"timestamp\":${System.currentTimeMillis() / 1000}}"
    }

    fun toPresenceOfflineJson(): JSONObject {
        val json = JSONObject()
        json.put("type", "presence_offline")
        json.put("protocol_version", 1)
        json.put("device_id", deviceId)
        json.put("timestamp", System.currentTimeMillis() / 1000)
        return json
    }

    fun toPresenceOfflineString(): String {
        return toPresenceOfflineJson().toString()
    }

    companion object {
        fun fromPresenceJson(json: JSONObject): Device? {
            val id = if (json.has("device_id")) json.optString("device_id") else return null
            if (id.isEmpty()) return null

            val type = json.optString("type", "presence_online")
            val name = json.optString("display_name", "Unknown Device")
            val plat = json.optString("platform", "unknown")
            val ts = json.optLong("timestamp", System.currentTimeMillis() / 1000)

            return Device(
                deviceId = id,
                displayName = name,
                platform = plat,
                isOnline = (type == "presence_online"),
                lastSeen = ts
            )
        }

        fun fromJsonString(jsonStr: String): Device? {
            return try {
                val json = JSONObject(jsonStr)
                fromPresenceJson(json)
            } catch (e: Exception) {
                null
            }
        }
    }
}
