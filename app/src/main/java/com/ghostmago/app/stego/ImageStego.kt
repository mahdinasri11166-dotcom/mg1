package com.ghostmago.app.stego

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.ghostmago.app.crypto.CryptoEngine
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * پنهانسازی LSB در تصویر (از Ghost SMS)
 */
object ImageStego {

    fun hide(bitmap: Bitmap, secret: String, password: String): Bitmap {
        val enc = CryptoEngine.encrypt(secret.toByteArray(Charsets.UTF_8), password.toCharArray())
        val data = ByteArray(4) { i -> (enc.size shr (24 - i * 8)).toByte() } + enc
        val bits = data.flatMap { b ->
            (7 downTo 0).map { bit -> ((b.toInt() shr bit) and 1) }
        }
        val w = bitmap.width
        val h = bitmap.height
        require(bits.size <= w * h * 3) { "تصویر کوچک است" }

        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        var bi = 0
        outer@ for (y in 0 until h) {
            for (x in 0 until w) {
                if (bi >= bits.size) break@outer
                var pixel = out.getPixel(x, y)
                var r = Color.red(pixel)
                var g = Color.green(pixel)
                var b = Color.blue(pixel)
                if (bi < bits.size) { r = (r and 0xFE) or bits[bi++]; }
                if (bi < bits.size) { g = (g and 0xFE) or bits[bi++]; }
                if (bi < bits.size) { b = (b and 0xFE) or bits[bi++]; }
                out.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        return out
    }

    fun extract(bitmap: Bitmap, password: String): String {
        val w = bitmap.width
        val h = bitmap.height
        val bits = mutableListOf<Int>()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = bitmap.getPixel(x, y)
                bits.add(Color.red(p) and 1)
                bits.add(Color.green(p) and 1)
                bits.add(Color.blue(p) and 1)
            }
        }
        require(bits.size >= 32) { "داده کافی نیست" }
        var dlen = 0
        for (i in 0 until 32) dlen = (dlen shl 1) or bits[i]
        require(dlen in 1 until 10_000_000) { "طول نامعتبر" }
        val total = 32 + dlen * 8
        require(bits.size >= total) { "داده ناقص" }
        val data = ByteArray(dlen) { i ->
            var v = 0
            for (b in 0 until 8) v = (v shl 1) or bits[32 + i * 8 + b]
            v.toByte()
        }
        return String(CryptoEngine.decrypt(data, password.toCharArray()), Charsets.UTF_8)
    }

    fun savePng(bitmap: Bitmap, file: File) {
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
