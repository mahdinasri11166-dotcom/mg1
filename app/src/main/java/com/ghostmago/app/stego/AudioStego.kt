package com.ghostmago.app.stego

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * LSB stego in PCM16 WAV data after 44-byte header.
 * Payload: magic(4) + len(4 LE) + encrypted bytes, bit-packed into LSB of samples.
 */
object AudioStego {
    private val MAGIC = byteArrayOf('G'.code.toByte(), 'M'.code.toByte(), 'A'.code.toByte(), 'S'.code.toByte())

    fun hide(wavBytes: ByteArray, payload: ByteArray): ByteArray {
        require(wavBytes.size > 44) { "فایل WAV کوچک است" }
        val header = wavBytes.copyOfRange(0, 44)
        val pcm = wavBytes.copyOfRange(44, wavBytes.size)
        val blob = MAGIC + intLe(payload.size) + payload
        val bitsNeeded = blob.size * 8
        require(pcm.size / 2 >= bitsNeeded) { "Audio capacity too small need $bitsNeeded samples" }

        val outPcm = pcm.copyOf()
        var bitIndex = 0
        for (b in blob) {
            for (i in 0 until 8) {
                val bit = (b.toInt() shr i) and 1
                val sampleIndex = bitIndex * 2
                if (sampleIndex + 1 >= outPcm.size) break
                outPcm[sampleIndex] = ((outPcm[sampleIndex].toInt() and 0xFE) or bit).toByte()
                bitIndex++
            }
        }
        return header + outPcm
    }

    fun extract(wavBytes: ByteArray): ByteArray {
        require(wavBytes.size > 44) { "فایل WAV کوچک است" }
        val pcm = wavBytes.copyOfRange(44, wavBytes.size)
        val maxBytes = pcm.size / 16 // safety
        val bits = BooleanArray(minOf(pcm.size / 2, maxBytes * 8))
        for (i in bits.indices) {
            val sampleIndex = i * 2
            bits[i] = (pcm[sampleIndex].toInt() and 1) == 1
        }
        fun readByte(startBit: Int): Int {
            var v = 0
            for (i in 0 until 8) {
                if (startBit + i >= bits.size) return -1
                if (bits[startBit + i]) v = v or (1 shl i)
            }
            return v
        }
        // magic
        val m0 = readByte(0); val m1 = readByte(8); val m2 = readByte(16); val m3 = readByte(24)
        if (m0 != MAGIC[0].toInt() || m1 != MAGIC[1].toInt() || m2 != MAGIC[2].toInt() || m3 != MAGIC[3].toInt()) {
            throw IllegalArgumentException("بدون داده پنهاننگاری")
        }
        val len = readByte(32) or (readByte(40) shl 8) or (readByte(48) shl 16) or (readByte(56) shl 24)
        if (len <= 0 || len > 2_000_000) throw IllegalArgumentException("طول نامعتبر: $len")
        val out = ByteArray(len)
        var bitPos = 64
        for (i in 0 until len) {
            val b = readByte(bitPos)
            if (b < 0) throw IllegalArgumentException("Truncated payload")
            out[i] = b.toByte()
            bitPos += 8
        }
        return out
    }

    private fun intLe(v: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
}
