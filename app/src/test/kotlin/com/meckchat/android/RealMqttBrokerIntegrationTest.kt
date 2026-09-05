package com.meckchat.android

import com.meckchat.android.core.AppConfig
import com.meckchat.android.core.Logger
import com.meckchat.android.network.ConnectionState
import com.meckchat.android.network.MqttSignalingManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RealMqttBrokerIntegrationTest {

    @Test
    fun testRealHiveMqBrokerTwoDeviceSignaling() {
        val uniqueSuffix = UUID.randomUUID().toString().substring(0, 8)
        val deviceIdA = "mc_test_android_a_$uniqueSuffix"
        val deviceIdB = "mc_test_android_b_$uniqueSuffix"

        val configA = AppConfig(
            deviceId = deviceIdA,
            displayName = "Test Android A",
            platform = "android",
            mqttBrokerHost = "broker.hivemq.com",
            mqttBrokerPort = 8883
        )

        val configB = AppConfig(
            deviceId = deviceIdB,
            displayName = "Test Android B",
            platform = "android",
            mqttBrokerHost = "broker.hivemq.com",
            mqttBrokerPort = 8883
        )

        val managerA = MqttSignalingManager(configA)
        val managerB = MqttSignalingManager(configB)

        Logger.info("IntegrationTest", "=== Starting 2-Device Real MQTT Test against HiveMQ TLS ===")

        try {
            // 1. Connect Device A
            managerA.connect(configA.mqttBrokerHost, configA.mqttBrokerPort, managerA.getCurrentDevice())

            // Wait for A to connect (up to 15s)
            val startTimeA = System.currentTimeMillis()
            while (!managerA.isConnected() && (System.currentTimeMillis() - startTimeA < 15000)) {
                Thread.sleep(200)
            }
            assertTrue("Device A should connect to HiveMQ broker over TLS", managerA.isConnected())
            Logger.info("IntegrationTest", "Device A successfully connected over TLS!")

            // 2. Connect Device B
            managerB.connect(configB.mqttBrokerHost, configB.mqttBrokerPort, managerB.getCurrentDevice())

            // Wait for B to connect (up to 15s)
            val startTimeB = System.currentTimeMillis()
            while (!managerB.isConnected() && (System.currentTimeMillis() - startTimeB < 15000)) {
                Thread.sleep(200)
            }
            assertTrue("Device B should connect to HiveMQ broker over TLS", managerB.isConnected())
            Logger.info("IntegrationTest", "Device B successfully connected over TLS!")

            // Allow subscriptions to register
            Thread.sleep(2000)

            // 3. Device A broadcasts discovery
            managerA.broadcastDiscovery()

            // Wait for Device B to discover Device A (up to 15s)
            val startDiscoveryB = System.currentTimeMillis()
            while (!managerB.discoveredDevices.value.any { it.deviceId == deviceIdA } &&
                (System.currentTimeMillis() - startDiscoveryB < 15000)) {
                Thread.sleep(200)
            }

            val bDiscoveredA = managerB.discoveredDevices.value.find { it.deviceId == deviceIdA }
            assertTrue("Device B should discover Device A via MQTT discovery", bDiscoveredA != null)
            assertEquals("Test Android A", bDiscoveredA?.displayName)
            assertTrue("Discovered Device A should be marked online", bDiscoveredA?.isOnline == true)
            Logger.info("IntegrationTest", "PASS: Device B discovered Device A: ${bDiscoveredA?.deviceId}")

            // 4. Device B broadcasts discovery
            managerB.broadcastDiscovery()

            // Wait for Device A to discover Device B (up to 15s)
            val startDiscoveryA = System.currentTimeMillis()
            while (!managerA.discoveredDevices.value.any { it.deviceId == deviceIdB } &&
                (System.currentTimeMillis() - startDiscoveryA < 15000)) {
                Thread.sleep(200)
            }

            val aDiscoveredB = managerA.discoveredDevices.value.find { it.deviceId == deviceIdB }
            assertTrue("Device A should discover Device B via MQTT discovery", aDiscoveredB != null)
            assertEquals("Test Android B", aDiscoveredB?.displayName)
            assertTrue("Discovered Device B should be marked online", aDiscoveredB?.isOnline == true)
            Logger.info("IntegrationTest", "PASS: Device A discovered Device B: ${aDiscoveredB?.deviceId}")

            Logger.info("IntegrationTest", "=== Two-device MQTT signaling test PASSED successfully! ===")

        } finally {
            managerA.disconnect()
            managerB.disconnect()
        }
    }
}
