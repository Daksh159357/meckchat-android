package com.meckchat.android.model

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

enum class FrameType(val code: Short) {
    HEARTBEAT(0x0001),
    CHAT_MESSAGE(0x0002),
    MESSAGE_ACK(0x0003),
    TYPING_INDICATOR(0x0004),
    FILE_TRANSFER_OFFER(0x0010),
    FILE_TRANSFER_ACCEPT(0x0011),
    FILE_TRANSFER_REJECT(0x0012),
    FILE_CHUNK(0x0013),
    FILE_COMPLETE(0x0014),
    FILE_CANCEL(0x0015);

    companion object {
        fun fromCode(code: Short): FrameType? = entries.find { it.code == code }
    }
}

data class P2PFrame(
    val type: FrameType,
    val payload: ByteArray
) {
    fun encode(): ByteArray {
        val magic: Short = 0x4D43 // 'M' 'C'
        val payloadLen = payload.size

        val totalHeaderLen = 2 + 2 + 4 + payloadLen
        val buffer = ByteBuffer.allocate(totalHeaderLen + 4).order(ByteOrder.BIG_ENDIAN)

        buffer.putShort(magic)
        buffer.putShort(type.code)
        buffer.putInt(payloadLen)
        if (payloadLen > 0) {
            buffer.put(payload)
        }

        // Calculate CRC32 over Header + Payload
        val crc = CRC32()
        crc.update(buffer.array(), 0, totalHeaderLen)
        buffer.putInt(crc.value.toInt())

        return buffer.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as P2PFrame
        if (type != other.type) return false
        return payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        const val MAGIC: Short = 0x4D43

        fun decode(bytes: ByteArray): P2PFrame? {
            if (bytes.size < 12) return null

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val magic = buffer.short
            if (magic != MAGIC) return null

            val typeCode = buffer.short
            val type = FrameType.fromCode(typeCode) ?: return null

            val length = buffer.int
            if (length < 0 || bytes.size != 12 + length) return null

            val payload = ByteArray(length)
            if (length > 0) {
                buffer.get(payload)
            }

            val expectedCrc = buffer.int.toLong() and 0xFFFFFFFFL

            val crc = CRC32()
            crc.update(bytes, 0, 8 + length)
            if (crc.value != expectedCrc) return null

            return P2PFrame(type, payload)
        }
    }
}
