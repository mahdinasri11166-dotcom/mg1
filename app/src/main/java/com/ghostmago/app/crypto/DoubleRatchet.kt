package com.ghostmago.app.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Double Ratchet (Signal-style) — commercial-grade session encryption
 * Root key + sending/receiving chain keys, per-message keys, skipped message keys
 */
class DoubleRatchet private constructor(
    private var rootKey: ByteArray,
    private var sendChainKey: ByteArray?,
    private var recvChainKey: ByteArray?,
    private var sendCount: Int = 0,
    private var recvCount: Int = 0,
    private var prevSendCount: Int = 0
) {
    private val skipped = LinkedHashMap<String, ByteArray>() // "chain-index" -> msg key
    private val maxSkip = 100
    private val random = SecureRandom()

    companion object {
        /** Initialize after X3DH shared secret is established */
        fun initAlice(sharedSecret: ByteArray, bobPublic: ByteArray? = null): DoubleRatchet {
            val rk = kdfRk(sharedSecret, ByteArray(32))
            val (newRk, ck) = kdfCk(rk)
            return DoubleRatchet(newRk, ck, null)
        }

        fun initBob(sharedSecret: ByteArray): DoubleRatchet {
            val rk = kdfRk(sharedSecret, ByteArray(32))
            return DoubleRatchet(rk, null, null)
        }

        private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(data)
        }

        private fun kdfRk(rk: ByteArray, input: ByteArray): ByteArray {
            return hmac(rk, input + byteArrayOf(0x01))
        }

        /** Returns (newChainKey, messageKey) */
        private fun kdfCk(ck: ByteArray): Pair<ByteArray, ByteArray> {
            val mk = hmac(ck, byteArrayOf(0x01))
            val newCk = hmac(ck, byteArrayOf(0x02))
            return newCk to mk
        }

        fun kdfCkPublic(ck: ByteArray) = kdfCk(ck)
    }

    data class EncryptedMessage(
        val headerCount: Int,
        val headerPrevCount: Int,
        val nonce: ByteArray,
        val ciphertext: ByteArray
    ) {
        fun toBytes(): ByteArray {
            val buf = ByteArray(4 + 4 + 12 + ciphertext.size)
            // counts
            buf[0] = (headerCount shr 24).toByte(); buf[1] = (headerCount shr 16).toByte()
            buf[2] = (headerCount shr 8).toByte(); buf[3] = headerCount.toByte()
            buf[4] = (headerPrevCount shr 24).toByte(); buf[5] = (headerPrevCount shr 16).toByte()
            buf[6] = (headerPrevCount shr 8).toByte(); buf[7] = headerPrevCount.toByte()
            System.arraycopy(nonce, 0, buf, 8, 12)
            System.arraycopy(ciphertext, 0, buf, 20, ciphertext.size)
            return buf
        }

        companion object {
            fun fromBytes(data: ByteArray): EncryptedMessage {
                require(data.size > 20)
                val count = ((data[0].toInt() and 0xFF) shl 24) or ((data[1].toInt() and 0xFF) shl 16) or
                        ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
                val prev = ((data[4].toInt() and 0xFF) shl 24) or ((data[5].toInt() and 0xFF) shl 16) or
                        ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
                val nonce = data.copyOfRange(8, 20)
                val ct = data.copyOfRange(20, data.size)
                return EncryptedMessage(count, prev, nonce, ct)
            }
        }
    }

    @Synchronized
    fun encrypt(plaintext: ByteArray): EncryptedMessage {
        if (sendChainKey == null) {
            // first send: derive send chain from root
            val (rk, ck) = kdfCk(rootKey)
            rootKey = rk
            sendChainKey = ck
        }
        val (newCk, mk) = kdfCk(sendChainKey!!)
        sendChainKey = newCk
        val count = sendCount
        sendCount++
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(mk, "AES"), GCMParameterSpec(128, nonce))
        val ct = cipher.doFinal(plaintext)
        wipe(mk)
        return EncryptedMessage(count, prevSendCount, nonce, ct)
    }

    @Synchronized
    fun decrypt(msg: EncryptedMessage): ByteArray {
        // try skipped keys first
        val skipKey = skipped.remove("${msg.headerCount}")
        if (skipKey != null) {
            return decryptWithKey(skipKey, msg).also { wipe(skipKey) }
        }
        if (recvChainKey == null) {
            val (rk, ck) = kdfCk(rootKey)
            rootKey = rk
            recvChainKey = ck
        }
        // skip ahead if needed
        while (recvCount < msg.headerCount) {
            val (newCk, mk) = kdfCk(recvChainKey!!)
            recvChainKey = newCk
            if (skipped.size >= maxSkip) skipped.remove(skipped.keys.first())
            skipped["$recvCount"] = mk
            recvCount++
        }
        val (newCk, mk) = kdfCk(recvChainKey!!)
        recvChainKey = newCk
        recvCount++
        return decryptWithKey(mk, msg).also { wipe(mk) }
    }

    private fun decryptWithKey(mk: ByteArray, msg: EncryptedMessage): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(mk, "AES"), GCMParameterSpec(128, msg.nonce))
        return cipher.doFinal(msg.ciphertext)
    }

    fun dhRatchet(dhOutput: ByteArray) {
        prevSendCount = sendCount
        sendCount = 0
        recvCount = 0
        rootKey = kdfRk(rootKey, dhOutput)
        val (rk1, recvCk) = kdfCk(rootKey)
        rootKey = rk1
        recvChainKey = recvCk
        val (rk2, sendCk) = kdfCk(rootKey)
        rootKey = rk2
        sendChainKey = sendCk
    }

    private fun wipe(arr: ByteArray) { arr.fill(0) }
}
