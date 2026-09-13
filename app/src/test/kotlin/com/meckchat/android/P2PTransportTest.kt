package com.meckchat.android

import com.meckchat.android.model.ChatMessage
import com.meckchat.android.model.FrameType
import com.meckchat.android.model.P2PFrame
import com.meckchat.android.network.P2PSocketClient
import com.meckchat.android.network.P2PSocketServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class P2PTransportTest {

    @Test
    fun testP2PServerAndClientLoopbackExchange() {
        val testPort = 17788
        val receivedMessages = mutableListOf<ChatMessage>()
        val receivedAcks = mutableListOf<String>()

        val messageLatch = CountDownLatch(1)
        val ackLatch = CountDownLatch(1)

        val server = P2PSocketServer(
            port = testPort,
            onMessageReceived = { msg ->
                synchronized(receivedMessages) {
                    receivedMessages.add(msg)
                }
                messageLatch.countDown()
            },
            onAckReceived = { ackId ->
                synchronized(receivedAcks) {
                    receivedAcks.add(ackId)
                }
            }
        )

        server.start()
        Thread.sleep(100)

        val client = P2PSocketClient(
            peerDeviceId = "mc_test_peer",
            onMessageReceived = {},
            onAckReceived = { ackId ->
                synchronized(receivedAcks) {
                    receivedAcks.add(ackId)
                }
                ackLatch.countDown()
            }
        )

        try {
            val connected = client.connect("127.0.0.1", testPort)
            assertTrue("Client should connect to server on loopback", connected)

            val msg = ChatMessage(
                messageId = "msg_test_p2p_1",
                senderDeviceId = "mc_client_dev",
                recipientDeviceId = "mc_server_dev",
                content = "Direct P2P Test Message",
                timestamp = 1778750000L
            )
            val frame = P2PFrame.createChatMessage(msg)
            val sent = client.sendFrame(frame)
            assertTrue("Frame should be sent successfully", sent)

            assertTrue("Server should receive message within 3s", messageLatch.await(3, TimeUnit.SECONDS))
            assertEquals(1, receivedMessages.size)
            assertEquals("Direct P2P Test Message", receivedMessages[0].content)
            assertEquals("msg_test_p2p_1", receivedMessages[0].messageId)

            assertTrue("Client should receive auto-ACK within 3s", ackLatch.await(3, TimeUnit.SECONDS))
            assertEquals(1, receivedAcks.size)
            assertEquals("msg_test_p2p_1", receivedAcks[0])

        } finally {
            client.disconnect()
            server.stop()
        }
    }
}
