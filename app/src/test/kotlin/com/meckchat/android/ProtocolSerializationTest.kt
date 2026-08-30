package com.meckchat.android

import com.meckchat.android.model.ChatMessage
import com.meckchat.android.model.Device
import com.meckchat.android.model.DiscoveryRequest
import org.junit.Assert.*
import org.junit.Test

class ProtocolSerializationTest {

    @Test
    fun testDeviceSerialization() {
        val device = Device(
            deviceId = "mc_android_test_123",
            displayName = "Android Pixel 8",
            platform = "android",
            isOnline = true
        )

        val jsonStr = device.toPresenceOnlineString()
        assertTrue(jsonStr.contains("\"device_id\":\"mc_android_test_123\""))
        assertTrue(jsonStr.contains("\"platform\":\"android\""))
        assertTrue(jsonStr.contains("\"type\":\"presence_online\""))
    }

    @Test
    fun testChatMessageCreation() {
        val msg = ChatMessage(
            messageId = "msg_123",
            senderDeviceId = "mc_sender",
            recipientDeviceId = "mc_receiver",
            content = "Encrypted Hello over P2P",
            timestamp = 1725000000L
        )

        assertEquals("msg_123", msg.messageId)
        assertEquals("mc_sender", msg.senderDeviceId)
        assertEquals("mc_receiver", msg.recipientDeviceId)
        assertEquals("Encrypted Hello over P2P", msg.content)
        assertEquals(1725000000L, msg.timestamp)
    }

    @Test
    fun testDiscoveryRequestCreation() {
        val req = DiscoveryRequest(
            deviceId = "mc_discovery_source",
            timestamp = 1725000000L
        )

        assertEquals("mc_discovery_source", req.deviceId)
        assertEquals(1725000000L, req.timestamp)
    }
}
