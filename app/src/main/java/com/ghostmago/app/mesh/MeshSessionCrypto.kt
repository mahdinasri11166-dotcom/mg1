package com.ghostmago.app.mesh

import android.util.Log
import com.ghostmago.app.crypto.CryptoEngine
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Argon2id group session key + AES-256-GCM with epoch rotation.
 * Epoch rotates keys so compromise of one epoch does not expose all history equally.
 */
class MeshSessionCrypto(
    private val groupPassword: String,
    private val groupId: String
) {
    private val epoch = AtomicInteger(0)
    @Volatile private var sessionKey: ByteArray? = null
    @Volatile private var prevKey: ByteArray? = null
    @Volatile private var lastRotateAt = System.currentTimeMillis()

    private val meshSalt: ByteArray = ByteArray(16).also { salt ->
        val h = CryptoEngine.sha256(("salt|$groupId").toByteArray())
        System.arraycopy(h, 0, salt, 0, 16)
    }

    private fun rootKey(): ByteArray {
        sessionKey?.let { return it }
        val k = CryptoEngine.meshSessionKey(groupPassword, groupId)
        sessionKey = k
        return k
    }

    fun currentEpoch(): Int = epoch.get()

    /** Derive epoch key: HMAC-SHA256(root, "epoch|n") */
    private fun keyForEpoch(e: Int): ByteArray {
        val root = rootKey()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(root, "HmacSHA256"))
        return mac.doFinal("epoch|$e|$groupId".toByteArray(Charsets.UTF_8))
    }

    /** Commander/peer can bump epoch; previous key kept briefly for decrypt window. */
    fun rotateEpoch(): Int {
        val next = epoch.incrementAndGet()
        prevKey = try { keyForEpoch(next - 1) } catch (_: Exception) { null }
        lastRotateAt = System.currentTimeMillis()
        return next
    }

    fun setEpoch(e: Int) {
        if (e == epoch.get()) return
        prevKey = try { keyForEpoch(epoch.get()) } catch (_: Exception) { null }
        epoch.set(e.coerceAtLeast(0))
        lastRotateAt = System.currentTimeMillis()
    }

    fun maybeAutoRotate(maxAgeMs: Long = 6 * 60 * 60 * 1000L): Boolean {
        if (System.currentTimeMillis() - lastRotateAt < maxAgeMs) return false
        rotateEpoch()
        return true
    }

    fun encryptPayload(json: String): ByteArray = try {
        val e = epoch.get()
        val key = keyForEpoch(e)
        val body = CryptoEngine.encryptWithKey(json.toByteArray(Charsets.UTF_8), key, meshSalt)
        // prefix: 0xE1 | epoch int32 BE | body
        val out = ByteArray(5 + body.size)
        out[0] = 0xE1.toByte()
        out[1] = ((e shr 24) and 0xFF).toByte()
        out[2] = ((e shr 16) and 0xFF).toByte()
        out[3] = ((e shr 8) and 0xFF).toByte()
        out[4] = (e and 0xFF).toByte()
        System.arraycopy(body, 0, out, 5, body.size)
        out
    } catch (ex: Exception) {
        Log.e(TAG, "encrypt failed", ex)
        ByteArray(0)
    }

    fun decryptPayload(data: ByteArray): JSONObject? {
        if (data.isEmpty()) return null
        return try {
            if (data[0] == 0xE1.toByte() && data.size > 5) {
                val e = ((data[1].toInt() and 0xFF) shl 24) or
                    ((data[2].toInt() and 0xFF) shl 16) or
                    ((data[3].toInt() and 0xFF) shl 8) or
                    (data[4].toInt() and 0xFF)
                val body = data.copyOfRange(5, data.size)
                val plain = try {
                    CryptoEngine.decryptWithKey(body, keyForEpoch(e))
                } catch (_: Exception) {
                    // try previous local epoch
                    CryptoEngine.decryptWithKey(body, keyForEpoch(epoch.get()))
                }
                // learn peer epoch
                if (e > epoch.get()) setEpoch(e)
                JSONObject(String(plain, Charsets.UTF_8))
            } else {
                // legacy packets without epoch header
                val plain = try {
                    CryptoEngine.decryptWithKey(data, rootKey())
                } catch (_: Exception) {
                    CryptoEngine.decrypt(data, groupPassword.toCharArray())
                }
                JSONObject(String(plain, Charsets.UTF_8))
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        sessionKey?.fill(0)
        prevKey?.fill(0)
        sessionKey = null
        prevKey = null
    }

    companion object {
        private const val TAG = "MeshSessionCrypto"
    }
}
