package com.ghostmago.app.stego

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.ghostmago.app.crypto.CryptoEngine
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Hide arbitrary file bytes inside a PNG via LSB (AES-256-GCM first).
 * Only recoverable with the password through this app.
 */
object FileStego {
    private val MAGIC = byteArrayOf('G'.code.toByte(), 'M'.code.toByte(), 'F'.code.toByte(), '1'.code.toByte())

    fun hideInPng(coverPng: ByteArray, fileBytes: ByteArray, password: String, quality: Int = 100): ByteArray {
        val bmp = BitmapFactory.decodeByteArray(coverPng, 0, coverPng.size)
            ?: throw IllegalArgumentException("تصویر پوششی نامعتبر")
        val mutable = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val enc = CryptoEngine.encrypt(fileBytes, password.toCharArray())
        val payload = MAGIC + ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(enc.size).array() + enc
        val bitsNeeded = payload.size * 8
        val capacity = mutable.width * mutable.height * 3 // RGB LSB
        require(bitsNeeded <= capacity) {
            "ظرفیت کم: نیاز $bitsNeeded بیت، موجود $capacity — تصویر بزرگتر انتخاب کنید"
        }
        var bitIdx = 0
        outer@ for (y in 0 until mutable.height) {
            for (x in 0 until mutable.width) {
                if (bitIdx >= bitsNeeded) break@outer
                var c = mutable.getPixel(x, y)
                var r = Color.red(c)
                var g = Color.green(c)
                var b = Color.blue(c)
                val a = Color.alpha(c)
                fun nextBit(): Int {
                    val byteI = bitIdx / 8
                    val shift = 7 - (bitIdx % 8)
                    val bit = (payload[byteI].toInt() shr shift) and 1
                    bitIdx++
                    return bit
                }
                if (bitIdx < bitsNeeded) r = (r and 0xFE) or nextBit()
                if (bitIdx < bitsNeeded) g = (g and 0xFE) or nextBit()
                if (bitIdx < bitsNeeded) b = (b and 0xFE) or nextBit()
                mutable.setPixel(x, y, Color.argb(a, r, g, b))
            }
        }
        val out = ByteArrayOutputStream()
        mutable.compress(Bitmap.CompressFormat.PNG, quality, out)
        return out.toByteArray()
    }

    fun extractFromPng(stegoPng: ByteArray, password: String): ByteArray {
        val bmp = BitmapFactory.decodeByteArray(stegoPng, 0, stegoPng.size)
            ?: throw IllegalArgumentException("تصویر نامعتبر")
        val bits = ArrayList<Int>(bmp.width * bmp.height * 3)
        for (y in 0 until bmp.height) {
            for (x in 0 until bmp.width) {
                val c = bmp.getPixel(x, y)
                bits += Color.red(c) and 1
                bits += Color.green(c) and 1
                bits += Color.blue(c) and 1
            }
        }
        fun readByte(startBit: Int): Int {
            var v = 0
            for (i in 0 until 8) {
                v = (v shl 1) or bits[startBit + i]
            }
            return v
        }
        val header = ByteArray(8) { i -> readByte(i * 8).toByte() }
        require(header.copyOfRange(0, 4).contentEquals(MAGIC)) { "این تصویر فایل پنهان مشکات نیست" }
        val len = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.BIG_ENDIAN).int
        require(len in 1..20_000_000) { "طول نامعتبر" }
        val enc = ByteArray(len) { i -> readByte(64 + i * 8).toByte() }
        return CryptoEngine.decrypt(enc, password.toCharArray())
    }
}
