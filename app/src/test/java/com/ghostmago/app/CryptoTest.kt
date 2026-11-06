package com.ghostmago.app

import com.ghostmago.app.crypto.CryptoEngine
import com.ghostmago.app.crypto.DoubleRatchet
import org.junit.Assert.*
import org.junit.Test

class CryptoTest {
    @Test
    fun aesRoundTrip() {
        val pw = "test-password-9".toCharArray()
        val ct = CryptoEngine.encrypt("hello".toByteArray(), pw)
        val pt = String(CryptoEngine.decrypt(ct, pw))
        assertEquals("hello", pt)
    }

    @Test
    fun ratchetRoundTrip() {
        val sk = ByteArray(32) { it.toByte() }
        val alice = DoubleRatchet.initAlice(sk.copyOf())
        val bob = DoubleRatchet.initBob(sk.copyOf())
        val msg = alice.encrypt("secret".toByteArray())
        val out = String(bob.decrypt(msg))
        assertEquals("secret", out)
    }
}
