package com.meckchat.android.model

import org.json.JSONObject

data class ChatMessage(
    val messageId: String,
    val senderDeviceId: String,
    val recipientDeviceId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis() / 1000,
    val replyToMessageId: String? = null
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("message_id", messageId)
        json.put("sender_device_id", senderDeviceId)
        json.put("recipient_device_id", recipientDeviceId)
        json.put("content", content)
        json.put("timestamp", timestamp)
        if (replyToMessageId != null) {
            json.put("reply_to_message_id", replyToMessageId)
        }
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): ChatMessage? {
            if (!json.has("message_id") || !json.has("sender_device_id") || !json.has("recipient_device_id")) {
                return null
            }
            return ChatMessage(
                messageId = json.getString("message_id"),
                senderDeviceId = json.getString("sender_device_id"),
                recipientDeviceId = json.getString("recipient_device_id"),
                content = if (json.has("content")) json.getString("content") else "",
                timestamp = if (json.has("timestamp")) json.getLong("timestamp") else (System.currentTimeMillis() / 1000),
                replyToMessageId = if (json.has("reply_to_message_id") && !json.isNull("reply_to_message_id")) json.getString("reply_to_message_id") else null
            )
        }
    }
}
