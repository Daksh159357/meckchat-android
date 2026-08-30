package com.meckchat.android.model

import org.json.JSONObject

data class DiscoveryRequest(
    val deviceId: String,
    val timestamp: Long = System.currentTimeMillis() / 1000
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("type", "discovery_request")
            put("protocol_version", 1)
            put("device_id", deviceId)
            put("timestamp", timestamp)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): DiscoveryRequest? {
            if (json.optString("type") != "discovery_request") return null
            val id = json.optString("device_id")
            if (id.isEmpty()) return null
            return DiscoveryRequest(
                deviceId = id,
                timestamp = json.optLong("timestamp", System.currentTimeMillis() / 1000)
            )
        }
    }
}

data class MessageAck(
    val messageId: String,
    val status: String = "delivered",
    val timestamp: Long = System.currentTimeMillis() / 1000
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("message_id", messageId)
            put("status", status)
            put("timestamp", timestamp)
        }
    }
}

data class FileOffer(
    val transferId: String,
    val fileName: String,
    val fileSize: Long,
    val sha256: String,
    val chunkSize: Int = 65536,
    val totalChunks: Int = 0
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("transfer_id", transferId)
            put("file_name", fileName)
            put("file_size", fileSize)
            put("sha256", sha256)
            put("chunk_size", chunkSize)
            put("total_chunks", totalChunks)
        }
    }
}
