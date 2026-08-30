package com.meckchat.android

import com.meckchat.android.model.FrameType
import com.meckchat.android.model.P2PFrame
import org.junit.Assert.*
import org.junit.Test

class P2PFrameTest {

    @Test
    fun testFrameEncodingAndDecoding() {
        val payload = "{\"text\":\"Hello MeckChat Frame\"}".toByteArray(Charsets.UTF_8)
        val frame = P2PFrame(
            type = FrameType.CHAT_MESSAGE,
            payload = payload
        )

        val encoded = frame.encode()
        assertTrue(encoded.size >= 12)

        val decoded = P2PFrame.decode(encoded)
        assertNotNull(decoded)
        assertEquals(FrameType.CHAT_MESSAGE, decoded?.type)
        assertArrayEquals(payload, decoded?.payload)
    }

    @Test
    fun testFrameCrcCorruptRejection() {
        val payload = "payload data".toByteArray(Charsets.UTF_8)
        val frame = P2PFrame(
            type = FrameType.HEARTBEAT,
            payload = payload
        )

        val encoded = frame.encode()
        // Corrupt one byte
        encoded[10] = (encoded[10].toInt() xor 0xFF).toByte()

        val decoded = P2PFrame.decode(encoded)
        assertNull("Corrupted frame must be rejected by CRC check", decoded)
    }
}
