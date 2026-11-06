package com.ghostmago.app.mesh

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Mesh packet binary format (ASCII comments only).
 */
object MeshPacket {
    val MAGIC = "MGO5".toByteArray()
    const val HEADER_SIZE = 4 + 1 + 1 + 16 + 16 + 16 + 1 + 8 + 1 + 2

    const val TYPE_BEACON = 1
    const val TYPE_DATA = 2
    const val TYPE_VOICE = 3
    const val TYPE_BROADCAST = 4
    const val TYPE_JOIN_REQ = 5
    const val TYPE_JOIN_ACCEPT = 6
    const val TYPE_JOIN_REJECT = 7
    const val TYPE_FILE = 9
    const val TYPE_COVER = 10
    const val TYPE_PRIVATE = 12
    const val TYPE_PRIV_HS = 13
    const val TYPE_ACK = 14
    const val TYPE_STORE = 15
    const val TYPE_IMAGE = 16
    const val TYPE_CHUNK = 18
    const val TYPE_RELAY = 19
    const val TYPE_X3DH = 17
    const val TYPE_REMOTE_WIPE = 21
    const val TYPE_DEVICE_POLICY = 22

    const val FLAG_EPHEMERAL = 2

    data class Parsed(
        val type: Int,
        val ttl: Int,
        val msgId: String,
        val originHash: String,
        val targetHash: String,
        val hops: Int,
        val timestamp: Double,
        val flags: Int,
        val payload: ByteArray
    )

    fun build(
        type: Int,
        ttl: Int,
        msgId: String,
        originHash: String,
        targetHash: String,
        hops: Int,
        payload: ByteArray,
        flags: Int = 0
    ): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC)
        buf.put(type.toByte())
        buf.put(ttl.toByte())
        putFixed(buf, msgId, 16)
        putFixed(buf, originHash, 16)
        putFixed(buf, targetHash, 16)
        buf.put(hops.toByte())
        buf.putDouble(System.currentTimeMillis() / 1000.0)
        buf.put(flags.toByte())
        buf.putShort(payload.size.toShort())
        buf.put(payload)
        return buf.array()
    }

    fun parse(data: ByteArray): Parsed? {
        if (data.size < HEADER_SIZE) return null
        if (!data.copyOfRange(0, 4).contentEquals(MAGIC)) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        buf.position(4)
        val type = buf.get().toInt() and 0xFF
        val ttl = buf.get().toInt() and 0xFF
        val msgId = getFixed(buf, 16)
        val origin = getFixed(buf, 16)
        val target = getFixed(buf, 16)
        val hops = buf.get().toInt() and 0xFF
        val ts = buf.double
        val flags = buf.get().toInt() and 0xFF
        val plen = buf.short.toInt() and 0xFFFF
        if (data.size < HEADER_SIZE + plen) return null
        val payload = ByteArray(plen)
        buf.get(payload)
        return Parsed(type, ttl, msgId, origin, target, hops, ts, flags, payload)
    }

    private fun putFixed(buf: ByteBuffer, s: String, len: Int) {
        val b = s.toByteArray(Charsets.UTF_8)
        buf.put(b, 0, minOf(b.size, len))
        if (b.size < len) buf.put(ByteArray(len - b.size))
    }

    private fun getFixed(buf: ByteBuffer, len: Int): String {
        val b = ByteArray(len)
        buf.get(b)
        return String(b, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
    }

    fun makeMsgId(seed: String = ""): String {
        val raw = "$seed-${System.nanoTime()}-${SecureRandom().nextInt()}"
        val dig = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return dig.take(8).joinToString("") { "%02x".format(it) }
    }

    fun hashId(id: String): String {
        val dig = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        return dig.take(8).joinToString("") { "%02x".format(it) }
    }
}
