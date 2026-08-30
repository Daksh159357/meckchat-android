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
        return JSONObject().apply {
            put("message_id", messageId)
            put("sender_device_id", senderDeviceId)
            put("recipient_device_id", recipientDeviceId)
            put("content", content)
            put("timestamp", timestamp)
            put("reply_to_message_id", replyToMessageId ?: JSONObject.NULL)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ChatMessage? {
            val msgId = json.optString("message_id")
            val sender = json.optString("sender_device_id")
            val recipient = json.optString("recipient_device_id")
            if (msgId.isEmpty() || sender.isEmpty() || recipient.isEmpty()) return null

            return ChatMessage(
                messageId = msgId,
                senderDeviceId = sender,
                recipientDeviceId = recipient,
                content = json.optString("content"),
                timestamp = json.optLong("timestamp", System.currentTimeMillis() / 1000),
                replyToMessageId = if (json.isNull("reply_to_message_id")) null else json.optString("reply_to_message_id")
            )
        }
    }
}
