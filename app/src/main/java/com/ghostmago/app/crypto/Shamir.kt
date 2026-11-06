package com.ghostmago.app.crypto

import java.security.SecureRandom

/**
 * Shamir Secret Sharing over GF(256) with Russian-peasant multiply (Rijndael poly 0x1B).
 * Line format: SHARE|<index>|<hex>|<checksum>
 */
object Shamir {
    private val random = SecureRandom()

    /** GF(256) multiply — no log tables (avoids init bugs). */
    private fun mul(a0: Int, b0: Int): Int {
        var a = a0 and 0xFF
        var b = b0 and 0xFF
        var p = 0
        repeat(8) {
            if (b and 1 != 0) p = p xor a
            val hi = a and 0x80
            a = (a shl 1) and 0xFF
            if (hi != 0) a = a xor 0x1B
            b = b shr 1
        }
        return p and 0xFF
    }

    private fun div(a: Int, b: Int): Int {
        if (b == 0) throw ArithmeticException("div0")
        if (a == 0) return 0
        // a / b = a * inv(b)
        return mul(a, inv(b))
    }

    private fun inv(a0: Int): Int {
        // a^254 = inv in GF(256)
        var a = a0 and 0xFF
        var r = 1
        for (i in 0 until 7) {
            a = mul(a, a)
            r = mul(r, a)
        }
        // After Fermat: a^{254} = a^{-1}; compute a^254 via square-multiply
        // Simpler extended: brute for 256-size field
        val x = a0 and 0xFF
        for (i in 1..255) {
            if (mul(x, i) == 1) return i
        }
        throw ArithmeticException("no inv")
    }

    private fun eval(coeffs: IntArray, x: Int): Int {
        var r = 0
        for (c in coeffs.reversed()) {
            r = mul(r, x) xor c
        }
        return r and 0xFF
    }

    fun split(secret: ByteArray, n: Int = 5, k: Int = 3): List<Pair<Int, ByteArray>> {
        require(k in 2..n && n <= 255)
        require(secret.isNotEmpty())
        val shares = Array(n) { ByteArray(secret.size) }
        for (pos in secret.indices) {
            val coeffs = IntArray(k)
            coeffs[0] = secret[pos].toInt() and 0xFF
            for (i in 1 until k) coeffs[i] = random.nextInt(256)
            for (i in 0 until n) {
                shares[i][pos] = eval(coeffs, i + 1).toByte()
            }
        }
        return (0 until n).map { (it + 1) to shares[it] }
    }

    fun recover(shares: List<Pair<Int, ByteArray>>): ByteArray {
        require(shares.size >= 2) { "need >= 2 shares" }
        val len = shares[0].second.size
        require(shares.all { it.second.size == len }) { "share length mismatch" }
        val result = ByteArray(len)
        for (pos in 0 until len) {
            var s = 0
            for (i in shares.indices) {
                val xi = shares[i].first
                val yi = shares[i].second[pos].toInt() and 0xFF
                var num = 1
                var den = 1
                for (j in shares.indices) {
                    if (i == j) continue
                    val xj = shares[j].first
                    num = mul(num, xj)
                    den = mul(den, xi xor xj)
                }
                s = s xor mul(yi, div(num, den))
            }
            result[pos] = (s and 0xFF).toByte()
        }
        return result
    }

    fun toShareLine(index: Int, data: ByteArray): String {
        val hex = data.joinToString("") { "%02x".format(it) }
        val sum = (index + data.fold(0) { a, b -> a + (b.toInt() and 0xFF) }) and 0xFF
        return "SHARE|$index|$hex|%02x".format(sum)
    }

    fun parseShareLine(line: String): Pair<Int, ByteArray>? {
        val t = line.trim().replace("\u200B", "").replace("\r", "")
        if (t.isEmpty()) return null
        if (t.startsWith("SHARE|")) {
            val p = t.split("|")
            if (p.size < 4) return null
            val idx = p[1].toIntOrNull() ?: return null
            val hex = p[2].lowercase().filter { it in "0123456789abcdef" }
            if (hex.length < 2 || hex.length % 2 != 0) return null
            val data = ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            val sum = (idx + data.fold(0) { a, b -> a + (b.toInt() and 0xFF) }) and 0xFF
            val expect = p[3].take(2).toIntOrNull(16) ?: return null
            if (sum != expect) return null
            return idx to data
        }
        if (t.contains(":")) {
            val parts = t.split(":", limit = 2)
            val idx = parts[0].removePrefix("S").trim().toIntOrNull() ?: return null
            return try {
                idx to android.util.Base64.decode(parts[1].trim(), android.util.Base64.DEFAULT)
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    fun recoverFromText(text: String, minShares: Int = 2): ByteArray {
        val list = text.lines().mapNotNull { parseShareLine(it) }.distinctBy { it.first }
        require(list.size >= minShares) {
            "حداقل $minShares تکه معتبر لازم است (الان ${list.size})"
        }
        require(list.map { it.second.size }.distinct().size == 1) { "طول تکهها یکسان نیست" }
        return recover(list)
    }
}
