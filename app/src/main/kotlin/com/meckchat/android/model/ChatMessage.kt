package com.meckchat.android.model

import org.json.JSONObject

data class ChatMessage(
    val messageId: String,
    val senderDeviceId: String,
    val recipientDeviceId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis() / 1000,
    val replyToMessageId: String? = null,
    val isDelivered: Boolean = false,
    val isSent: Boolean = true,
    val isFailed: Boolean = false
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
            val msgId = when {
                json.has("message_id") -> json.getString("message_id")
                json.has("id") -> json.getString("id")
                else -> return null
            }
            val sender = when {
                json.has("sender_device_id") -> json.getString("sender_device_id")
                json.has("sender") -> json.getString("sender")
                json.has("from") -> json.getString("from")
                else -> ""
            }
            val recipient = when {
                json.has("recipient_device_id") -> json.getString("recipient_device_id")
                json.has("recipient") -> json.getString("recipient")
                json.has("to") -> json.getString("to")
                else -> ""
            }
            val text = when {
                json.has("content") -> json.getString("content")
                json.has("text") -> json.getString("text")
                json.has("message") -> json.getString("message")
                else -> ""
            }
            val ts = if (json.has("timestamp")) json.optLong("timestamp", System.currentTimeMillis() / 1000) else (System.currentTimeMillis() / 1000)
            val replyId = if (json.has("reply_to_message_id") && !json.isNull("reply_to_message_id")) json.getString("reply_to_message_id") else null

            return ChatMessage(
                messageId = msgId,
                senderDeviceId = sender,
                recipientDeviceId = recipient,
                content = text,
                timestamp = ts,
                replyToMessageId = replyId
            )
        }
    }
}

