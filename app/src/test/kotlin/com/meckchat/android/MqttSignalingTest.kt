package com.meckchat.android

import com.meckchat.android.core.AppConfig
import com.meckchat.android.model.Device
import com.meckchat.android.network.MqttSignalingManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttSignalingTest {

    @Test
    fun testTopicGeneration() {
        val deviceId = "mc_test_device_123"

        assertEquals(
            "meckchat/v1/presence/online/mc_test_device_123",
            MqttSignalingManager.getPresenceOnlineTopic(deviceId)
        )
        assertEquals(
            "meckchat/v1/presence/offline/mc_test_device_123",
            MqttSignalingManager.getPresenceOfflineTopic(deviceId)
        )
        assertEquals(
            "meckchat/v1/discovery",
            MqttSignalingManager.TOPIC_DISCOVERY
        )
    }

    @Test
    fun testDeviceParsingFromValidPresenceOnlineJson() {
        val jsonString = """
            {
                "type": "presence_online",
                "protocol_version": 1,
                "device_id": "mc_android_peer_456",
                "display_name": "Pixel 9 Pro",
                "platform": "android",
                "timestamp": 1725300000
            }
        """.trimIndent()

        val json = JSONObject(jsonString)
        val device = Device.fromPresenceJson(json)

        assertNotNull(device)
        assertEquals("mc_android_peer_456", device?.deviceId)
        assertEquals("Pixel 9 Pro", device?.displayName)
        assertEquals("android", device?.platform)
        assertTrue(device?.isOnline == true)
        assertEquals(1725300000L, device?.lastSeen)
    }

    @Test
    fun testDeviceParsingFromValidPresenceOfflineJson() {
        val jsonString = """
            {
                "type": "presence_offline",
                "protocol_version": 1,
                "device_id": "mc_android_peer_456",
                "timestamp": 1725300100
            }
        """.trimIndent()

        val json = JSONObject(jsonString)
        val device = Device.fromPresenceJson(json)

        assertNotNull(device)
        assertEquals("mc_android_peer_456", device?.deviceId)
        assertFalse(device?.isOnline == true)
    }

    @Test
    fun testMalformedMessageHandlingDoesNotCrash() {
        val config = AppConfig(deviceId = "mc_my_device_self")
        val manager = MqttSignalingManager(config)

        // Test with invalid json strings
        val malformedPayloads = listOf(
            "",
            "not a json at all",
            "{",
            "{\"type\": 12345}",
            "{\"device_id\": \"\"}",
            "null",
            "{invalid: json syntax}"
        )

        for (malformed in malformedPayloads) {
            // Should handle gracefully without throwing exception
            try {
                manager.handleIncomingMessage(MqttSignalingManager.TOPIC_DISCOVERY, malformed)
            } catch (e: Exception) {
                org.junit.Assert.fail("Handling malformed payload threw exception: ${e.message}")
            }
        }
    }

    @Test
    fun testIncomingDiscoveryMessageUpdatesState() {
        val myDeviceId = "mc_my_device_self"
        val config = AppConfig(deviceId = myDeviceId)
        val manager = MqttSignalingManager(config)

        val peerDeviceId = "mc_remote_peer_789"
        val peerOnlinePayload = """
            {
                "type": "presence_online",
                "protocol_version": 1,
                "device_id": "$peerDeviceId",
                "display_name": "Samsung Galaxy",
                "platform": "android",
                "timestamp": 1725300000
            }
        """.trimIndent()

        // 1. Process online announcement
        manager.handleIncomingMessage(MqttSignalingManager.TOPIC_DISCOVERY, peerOnlinePayload)

        val discoveredList = manager.discoveredDevices.value
        assertEquals(1, discoveredList.size)
        assertEquals(peerDeviceId, discoveredList[0].deviceId)
        assertEquals("Samsung Galaxy", discoveredList[0].displayName)
        assertTrue(discoveredList[0].isOnline)

        // 2. Process offline announcement for the same device
        val peerOfflinePayload = """
            {
                "type": "presence_offline",
                "protocol_version": 1,
                "device_id": "$peerDeviceId",
                "timestamp": 1725300100
            }
        """.trimIndent()

        manager.handleIncomingMessage(MqttSignalingManager.TOPIC_DISCOVERY, peerOfflinePayload)

        val updatedList = manager.discoveredDevices.value
        assertEquals(1, updatedList.size)
        assertFalse(updatedList[0].isOnline)
    }

    @Test
    fun testSelfAnnouncementIsIgnored() {
        val myDeviceId = "mc_my_device_self"
        val config = AppConfig(deviceId = myDeviceId)
        val manager = MqttSignalingManager(config)

        val selfPayload = """
            {
                "type": "presence_online",
                "protocol_version": 1,
                "device_id": "$myDeviceId",
                "display_name": "Myself",
                "platform": "android",
                "timestamp": 1725300000
            }
        """.trimIndent()

        manager.handleIncomingMessage(MqttSignalingManager.TOPIC_DISCOVERY, selfPayload)

        val discoveredList = manager.discoveredDevices.value
        assertEquals(0, discoveredList.size)
    }

    @Test
    fun testIncomingDiscoveryWithEndpointsUpdatesState() {
        val myDeviceId = "mc_my_device_self"
        val peerDeviceId = "mc_12345678-1234-4abc-8def-123456789abc"
        val config = AppConfig(deviceId = myDeviceId)
        val manager = MqttSignalingManager(config)

        val discoveryPayload = """
            {
                "protocol_version": 1,
                "device_id": "$peerDeviceId",
                "device_name": "mc_client_unit_test",
                "platform": "linux",
                "endpoints": [
                    {
                        "type": "tcp",
                        "host": "192.168.1.100",
                        "port": 7788
                    }
                ],
                "timestamp": 1778750000
            }
        """.trimIndent()

        manager.handleIncomingMessage(MqttSignalingManager.TOPIC_DISCOVERY, discoveryPayload)

        val discoveredList = manager.discoveredDevices.value
        assertEquals(1, discoveredList.size)
        val peer = discoveredList[0]
        assertEquals(peerDeviceId, peer.deviceId)
        assertEquals("mc_client_unit_test", peer.displayName)
        assertEquals("linux", peer.platform)
        assertEquals(1, peer.endpoints.size)
        assertEquals("192.168.1.100", peer.endpoints[0].host)
        assertEquals(7788, peer.endpoints[0].port)
        assertTrue(peer.isOnline)
    }

    @Test
    fun testRemoveDeviceAndClearOfflineDevices() {
        val config = AppConfig(deviceId = "mc_self")
        val manager = MqttSignalingManager(config)

        val device1 = Device("mc_peer_1", "Peer 1", "android", isOnline = true)
        val device2 = Device("mc_peer_2", "Peer 2", "linux", isOnline = false)
        val device3 = Device("mc_peer_3", "Peer 3", "windows", isOnline = false)

        manager.handleIncomingMessage(
            MqttSignalingManager.TOPIC_DISCOVERY,
            device1.toPresenceOnlineString()
        )
        manager.handleIncomingMessage(
            MqttSignalingManager.TOPIC_DISCOVERY,
            device2.toPresenceOnlineString()
        )
        manager.handleIncomingMessage(
            MqttSignalingManager.TOPIC_DISCOVERY,
            device3.toPresenceOnlineString()
        )

        // Mark 2 and 3 offline
        manager.handleIncomingMessage(
            MqttSignalingManager.TOPIC_DISCOVERY,
            device2.toPresenceOfflineString()
        )
        manager.handleIncomingMessage(
            MqttSignalingManager.TOPIC_DISCOVERY,
            device3.toPresenceOfflineString()
        )

        assertEquals(3, manager.discoveredDevices.value.size)

        // Remove single device
        manager.removeDevice("mc_peer_2")
        assertEquals(2, manager.discoveredDevices.value.size)
        assertTrue(manager.discoveredDevices.value.none { it.deviceId == "mc_peer_2" })

        // Clear remaining offline devices
        manager.clearOfflineDevices()
        assertEquals(1, manager.discoveredDevices.value.size)
        assertEquals("mc_peer_1", manager.discoveredDevices.value.first().deviceId)
        assertTrue(manager.discoveredDevices.value.first().isOnline)
    }

    @Test
    fun testTestWorkerIsIgnored() {
        val config = AppConfig(deviceId = "mc_self")
        val manager = MqttSignalingManager(config)

        val testWorkerDevice = Device("mc_test_ci_12345", "CI Test Worker", "linux", isOnline = true)
        val linuxClientDevice = Device("mc_client_unit_test", "Linux Client", "linux", isOnline = true)
        val regularDevice = Device("mc_android_friend", "Friend Phone", "android", isOnline = true)

        manager.handleIncomingMessage(MqttSignalingManager.TOPIC_DISCOVERY, testWorkerDevice.toPresenceOnlineString())
        manager.handleIncomingMessage(MqttSignalingManager.TOPIC_DISCOVERY, linuxClientDevice.toPresenceOnlineString())
        manager.handleIncomingMessage(MqttSignalingManager.TOPIC_DISCOVERY, regularDevice.toPresenceOnlineString())

        // testWorkerDevice should be ignored, linuxClientDevice and regularDevice should be discovered
        assertEquals(2, manager.discoveredDevices.value.size)
        assertTrue(manager.discoveredDevices.value.any { it.deviceId == "mc_client_unit_test" })
        assertTrue(manager.discoveredDevices.value.any { it.deviceId == "mc_android_friend" })
    }
}


