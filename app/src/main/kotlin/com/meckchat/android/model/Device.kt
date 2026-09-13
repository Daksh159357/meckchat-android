package com.meckchat.android.model

import org.json.JSONArray
import org.json.JSONObject

data class Endpoint(
    val type: String = "tcp",
    val host: String,
    val port: Int = 7788
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("type", type)
            put("host", host)
            put("port", port)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): Endpoint? {
            val host = json.optString("host", "")
            if (host.isEmpty()) return null
            val type = json.optString("type", "tcp")
            val port = json.optInt("port", 7788)
            return Endpoint(type = type, host = host, port = port)
        }
    }
}

enum class P2PConnectionState {
    DISCOVERING,
    P2P_CONNECTING,
    P2P_CONNECTED,
    P2P_DISCONNECTED,
    REDISCOVERING
}

data class Device(
    val deviceId: String,
    var displayName: String,
    val platform: String,
    var isOnline: Boolean = true,
    var lastSeen: Long = System.currentTimeMillis() / 1000,
    var virtualIp: String? = null,
    var endpoints: List<Endpoint> = emptyList(),
    var p2pState: P2PConnectionState = P2PConnectionState.DISCOVERING
) {
    fun toDiscoveryJson(): JSONObject {
        val json = JSONObject()
        json.put("protocol_version", 1)
        json.put("device_id", deviceId)
        json.put("device_name", displayName)
        json.put("platform", platform.lowercase())
        val arr = JSONArray()
        for (ep in endpoints) {
            arr.put(ep.toJson())
        }
        json.put("endpoints", arr)
        json.put("timestamp", System.currentTimeMillis() / 1000)
        return json
    }

    fun toDiscoveryString(): String {
        return toDiscoveryJson().toString()
    }

    fun toPresenceOnlineJson(): JSONObject {
        val json = JSONObject()
        json.put("protocol_version", 1)
        json.put("device_id", deviceId)
        json.put("platform", platform.lowercase())
        json.put("timestamp", System.currentTimeMillis() / 1000)
        return json
    }

    fun toPresenceOnlineString(): String {
        return toPresenceOnlineJson().toString()
    }

    fun toPresenceOfflineJson(): JSONObject {
        val json = JSONObject()
        json.put("protocol_version", 1)
        json.put("device_id", deviceId)
        json.put("type", "presence_offline")
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
            val name = when {
                json.has("device_name") && json.optString("device_name").isNotEmpty() -> json.optString("device_name")
                json.has("display_name") && json.optString("display_name").isNotEmpty() -> json.optString("display_name")
                json.has("name") && json.optString("name").isNotEmpty() -> json.optString("name")
                json.has("hostname") && json.optString("hostname").isNotEmpty() -> json.optString("hostname")
                else -> id
            }
            val plat = when {
                json.has("platform") && json.optString("platform").isNotEmpty() -> json.optString("platform")
                json.has("os") && json.optString("os").isNotEmpty() -> json.optString("os")
                else -> "linux"
            }
            val ts = json.optLong("timestamp", System.currentTimeMillis() / 1000)

            val endpointsList = mutableListOf<Endpoint>()
            if (json.has("endpoints")) {
                val arr = json.optJSONArray("endpoints")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i)
                        if (obj != null) {
                            Endpoint.fromJson(obj)?.let { endpointsList.add(it) }
                        }
                    }
                }
            } else if (json.has("ip") || json.has("host")) {
                val h = if (json.has("ip")) json.optString("ip") else json.optString("host")
                val p = json.optInt("port", 7788)
                if (h.isNotEmpty()) {
                    endpointsList.add(Endpoint(host = h, port = p))
                }
            }

            return Device(
                deviceId = id,
                displayName = name,
                platform = plat,
                isOnline = (type != "presence_offline"),
                lastSeen = ts,
                endpoints = endpointsList
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

