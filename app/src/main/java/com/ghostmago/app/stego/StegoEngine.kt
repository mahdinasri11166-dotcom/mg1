package com.ghostmago.app.stego

import com.ghostmago.app.crypto.CryptoEngine

/**
 * Zero-width steganography safe for Persian:
 * Visible cover text stays intact; payload is a block of invisible
 * characters APPENDED at the end (not between letters), so words
 * do not break (سلام stays سلام).
 */
object StegoEngine {

    private const val ZW0 = '\u200B' // bit 0
    private const val ZW1 = '\u200C' // bit 1
    // Optional separator (zero-width no-break space) before payload block
    private const val ZW_MARK = '\uFEFF'

    private val COVERS = listOf(
        "سلام خوبی چه خبر امیدوارم حالت خوب باشه امروز گفتم یه پیام بدم احوالپرسی کنم کار و بارت چطوره هوا چطوره خبری نیست ازت دلم برات تنگ شده موفق باشی",
        "سلام رفیق درباره کار امروز میخواستم بگم که همه چیز مرتب پیش میره اگر فرصت داشتی یه تماس بگیر ببینم چیکار میکنی امیدوارم روز خوبی داشته باشی",
        "سلام دوست عزیز این پیام برای هماهنگی هست لطفا هر وقت دیدی یک تایید کوتاه بفرست ممنون از همکاری تو روزت بخیر",
        "سلام امروز هوا خوبه و ترافیک سبکتره اگر برنامه نداشتی عصر یه دور میشه زد خبرم کن ببینم چی میگی"
    )

    private val SMS_COVERS = listOf(
        "سلام خوبی چه خبر امیدوارم روزت خوب باشه و کار و بارت مرتب پیش بره هر وقت دیدی خبر بده",
        "سلام رفیق حالت چطوره امروز چیکار میکنی اگر فرصت داشتی یک پیام بده ممنون",
        "سلام دوست عزیز این پیام برای احوالپرسیه امیدوارم حالت خوب باشه روزت بخیر",
        "سلام امروز هوا خوبه اگر برنامه نداشتی عصر خبر بده ببینم چی میگی موفق باشی"
    )

    fun getDefaultCover(index: Int = 0): String = COVERS.getOrElse(index) { COVERS[0] }

    fun getSmsCover(index: Int = 0): String = SMS_COVERS.getOrElse(index) { SMS_COVERS[0] }

    private fun buildPayload(secret: String, password: String): ByteArray {
        val plain = secret.toByteArray(Charsets.UTF_8)
        val compressed = try {
            val c = com.ghostmago.app.util.SecurityUtils.compress(plain)
            if (c.size < plain.size) c else plain
        } catch (_: Exception) {
            plain
        }
        return CryptoEngine.encrypt(compressed, password.toCharArray())
    }

    private fun openPayload(encrypted: ByteArray, password: String): String {
        val dec = CryptoEngine.decrypt(encrypted, password.toCharArray())
        val out = try {
            com.ghostmago.app.util.SecurityUtils.decompress(dec)
        } catch (_: Exception) {
            dec
        }
        return String(out, Charsets.UTF_8)
    }

    private fun bitsToZw(bits: String): String {
        val sb = StringBuilder(bits.length)
        for (b in bits) {
            sb.append(if (b == '0') ZW0 else ZW1)
        }
        return sb.toString()
    }

    private fun payloadToBits(encrypted: ByteArray): String {
        val payload = byteArrayOf('G'.code.toByte()) +
            byteArrayOf((encrypted.size shr 8).toByte(), (encrypted.size and 0xFF).toByte()) +
            encrypted
        return payload.joinToString("") {
            String.format("%8s", Integer.toBinaryString(it.toInt() and 0xFF)).replace(' ', '0')
        }
    }

    /**
     * Cover text unchanged + invisible payload block at the END.
     * Persian letters stay joined.
     */
    fun encode(cover: String, secret: String, password: String): String {
        val cleanCover = cover
            .replace(Regex("[\\u200B\\u200C\\u200D\\u2060\\uFEFF]"), "")
            .trim()
            .ifBlank { getDefaultCover(0) }
        val encrypted = buildPayload(secret, password)
        val bits = payloadToBits(encrypted)
        // Append only — never insert between characters
        return cleanCover + ZW_MARK + bitsToZw(bits)
    }

    /** Same invisible method for SMS (no visible GM: tag). */
    fun encodeSms(cover: String, secret: String, password: String): String {
        require(secret.length <= 80) {
            "برای پیامک، پیام مخفی حداکثر ۸۰ کاراکتر باشد"
        }
        val c = cover
            .replace(Regex("[\\u200B\\u200C\\u200D\\u2060\\uFEFF]"), "")
            .trim()
            .ifBlank { getSmsCover(0) }
        return encode(c, secret, password)
    }

    fun decode(stegoText: String, password: String): String {
        val bits = StringBuilder()
        for (ch in stegoText) {
            when (ch) {
                ZW0 -> bits.append('0')
                ZW1 -> bits.append('1')
            }
        }
        require(bits.length >= 24) {
            "داده پنهان کافی نیست (ممکن است کاراکترهای نامرئی حذف شده باشند)"
        }
        val headerBytes = ByteArray(3) { i ->
            bits.substring(i * 8, i * 8 + 8).toInt(2).toByte()
        }
        require(headerBytes[0] == 'G'.code.toByte()) {
            "هدر نامعتبر — این متن پنهاننگاری معتبر نیست"
        }
        val dlen = ((headerBytes[1].toInt() and 0xFF) shl 8) or (headerBytes[2].toInt() and 0xFF)
        require(dlen in 1 until 100_000) { "طول نامعتبر" }
        val totalBits = 24 + dlen * 8
        require(bits.length >= totalBits) { "داده ناقص" }
        val payload = ByteArray(dlen) { i ->
            bits.substring(24 + i * 8, 24 + i * 8 + 8).toInt(2).toByte()
        }
        return openPayload(payload, password)
    }

    fun looksLikeStego(text: String): Boolean {
        if (text.isEmpty()) return false
        var zw = 0
        for (ch in text) {
            val c = ch.code
            if (c == 0x200B || c == 0x200C) zw++
        }
        return zw >= 24
    }

    fun hasHidden(text: String): Boolean = looksLikeStego(text)

    /** Visible text only (for display/preview without payload). */
    fun visibleOnly(text: String): String =
        text.replace(Regex("[\\u200B\\u200C\\u200D\\u2060\\uFEFF]"), "")
}
