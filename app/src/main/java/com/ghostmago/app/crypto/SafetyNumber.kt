package com.ghostmago.app.crypto

import java.security.MessageDigest

object SafetyNumber {

    fun generate(myId: String, myPub: ByteArray, peerId: String, peerPub: ByteArray): String {
        var a = "$myId:${android.util.Base64.encodeToString(myPub, android.util.Base64.NO_WRAP)}"
        var b = "$peerId:${android.util.Base64.encodeToString(peerPub, android.util.Base64.NO_WRAP)}"
        if (a > b) { val t = a; a = b; b = t }
        val h = MessageDigest.getInstance("SHA-512").digest("$a|$b".toByteArray())
        val digits = StringBuilder()
        for (byte in h.take(30)) {
            digits.append("%03d".format(byte.toInt() and 0xFF).take(2))
        }
        while (digits.length < 60) digits.append('0')
        val d = digits.substring(0, 60)
        val groups = (0 until 12).map { d.substring(it * 5, it * 5 + 5) }
        return groups.take(6).joinToString(" ") + "\n" + groups.drop(6).joinToString(" ")
    }

    fun fingerprint(pub: ByteArray): String {
        val h = MessageDigest.getInstance("SHA-256").digest(pub)
        return h.take(8).joinToString(" ") { "%02X".format(it) }
    }
}
