package com.ghostmago.app.mesh

import com.ghostmago.app.crypto.CryptoEngine
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Sealed Sender: envelope encrypted so network observers cannot see sender id.
 * Outer layer uses group password; inner retains real origin for recipient after decrypt.
 */
object SealedSender {
    private val rnd = SecureRandom()

    fun seal(
        realOriginHash: String,
        realNick: String,
        innerPayload: ByteArray,
        groupPassword: String
    ): ByteArray {
        val inner = JSONObject().apply {
            put("o", realOriginHash)
            put("n", realNick)
            put("d", android.util.Base64.encodeToString(innerPayload, android.util.Base64.NO_WRAP))
            put("pad", android.util.Base64.encodeToString(ByteArray(8 + rnd.nextInt(24)).also { rnd.nextBytes(it) }, android.util.Base64.NO_WRAP))
        }.toString().toByteArray(Charsets.UTF_8)
        return CryptoEngine.encrypt(inner, groupPassword.toCharArray())
    }

    data class Opened(val originHash: String, val nick: String, val payload: ByteArray)

    fun open(sealed: ByteArray, groupPassword: String): Opened? {
        return try {
            val plain = CryptoEngine.decrypt(sealed, groupPassword.toCharArray())
            val o = JSONObject(String(plain, Charsets.UTF_8))
            val data = android.util.Base64.decode(o.getString("d"), android.util.Base64.NO_WRAP)
            Opened(o.getString("o"), o.optString("n", "?"), data)
        } catch (_: Exception) { null }
    }
}
