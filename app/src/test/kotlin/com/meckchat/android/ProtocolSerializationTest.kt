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

        val json = device.toPresenceOnlineJson()
        assertEquals("mc_android_test_123", json.getString("device_id"))
        assertEquals("android", json.getString("platform"))
        assertEquals("presence_online", json.getString("type"))

        val deserialized = Device.fromPresenceJson(json)
        assertNotNull(deserialized)
        assertEquals("mc_android_test_123", deserialized?.deviceId)
        assertEquals("Android Pixel 8", deserialized?.displayName)
        assertEquals("android", deserialized?.platform)
        assertTrue(deserialized?.isOnline == true)
    }

    @Test
    fun testChatMessageSerialization() {
        val msg = ChatMessage(
            messageId = "msg_123",
            senderDeviceId = "mc_sender",
            recipientDeviceId = "mc_receiver",
            content = "Encrypted Hello over P2P",
            timestamp = 1725000000L
        )

        val json = msg.toJson()
        val deserialized = ChatMessage.fromJson(json)
        assertNotNull(deserialized)
        assertEquals("msg_123", deserialized?.messageId)
        assertEquals("mc_sender", deserialized?.senderDeviceId)
        assertEquals("mc_receiver", deserialized?.recipientDeviceId)
        assertEquals("Encrypted Hello over P2P", deserialized?.content)
        assertEquals(1725000000L, deserialized?.timestamp)
    }

    @Test
    fun testDiscoveryRequestSerialization() {
        val req = DiscoveryRequest(
            deviceId = "mc_discovery_source",
            timestamp = 1725000000L
        )

        val json = req.toJson()
        val deserialized = DiscoveryRequest.fromJson(json)
        assertNotNull(deserialized)
        assertEquals("mc_discovery_source", deserialized?.deviceId)
        assertEquals(1725000000L, deserialized?.timestamp)
    }
}
