package com.ghostmago.app.util

import android.content.Context
import android.provider.Settings
import com.ghostmago.app.crypto.CryptoEngine
import org.json.JSONObject
import java.security.MessageDigest
import android.util.Base64

/**
 * Offline team license (HMAC-bound to device) + optional Play Billing hook.
 * For enterprise offline mesh: commander generates license tokens offline.
 */
object LicenseManager {
    private const val PREF_LICENSE = "license_token"
    private const val PREF_OK = "license_ok"
    /** Change before release distribution */
    private const val TEAM_SECRET = "GhostMago|MrElf|License|v1"

    fun isLicensed(context: Context): Boolean {
        val p = SecurityUtils.encryptedPrefs(context)
        if (p.getBoolean(PREF_OK, false)) {
            val token = p.getString(PREF_LICENSE, "") ?: return false
            return verifyToken(context, token)
        }
        // First install grace: allow until user sets/activates (dev-friendly)
        return p.getBoolean("license_grace", true)
    }

    fun activate(context: Context, token: String): Boolean {
        val ok = verifyToken(context, token.trim())
        SecurityUtils.encryptedPrefs(context).edit()
            .putBoolean(PREF_OK, ok)
            .putString(PREF_LICENSE, token.trim())
            .putBoolean("license_grace", !ok)
            .apply()
        SecurityUtils.audit(context, if (ok) "LICENSE_OK" else "LICENSE_FAIL")
        return ok
    }

    fun clearGrace(context: Context) {
        SecurityUtils.encryptedPrefs(context).edit().putBoolean("license_grace", false).apply()
    }

    fun deviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        return MessageDigest.getInstance("SHA-256")
            .digest(androidId.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
    }

    /** Generate offline license for a device fingerprint (commander tool). */
    fun generateToken(deviceFingerprint: String, daysValid: Int = 365): String {
        val exp = System.currentTimeMillis() / 1000 + daysValid * 86400L
        val payload = JSONObject()
            .put("d", deviceFingerprint)
            .put("e", exp)
            .put("v", 1)
            .toString()
        val sig = hmac(payload)
        return Base64.encodeToString(
            (payload + "|" + sig).toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
    }

    fun generateForThisDevice(context: Context, daysValid: Int = 365): String =
        generateToken(deviceId(context), daysValid)

    private fun verifyToken(context: Context, token: String): Boolean {
        return try {
            val raw = String(Base64.decode(token, Base64.NO_WRAP), Charsets.UTF_8)
            val parts = raw.split("|", limit = 2)
            if (parts.size != 2) return false
            val payload = parts[0]
            val sig = parts[1]
            if (sig != hmac(payload)) return false
            val o = JSONObject(payload)
            val exp = o.optLong("e", 0)
            if (exp > 0 && System.currentTimeMillis() / 1000 > exp) return false
            val d = o.optString("d", "")
            d == deviceId(context) || d == "*"
            // d=="*" = org-wide license
        } catch (_: Exception) {
            false
        }
    }

    private fun hmac(data: String): String {
        val mac = CryptoEngine.hmacSha256(TEAM_SECRET.toByteArray(), data.toByteArray())
        return mac.take(16).joinToString("") { "%02x".format(it) }
    }

    /** Stub for future Google Play Billing integration */
    fun verifyWithPlayBilling(context: Context, onResult: (Boolean) -> Unit) {
        onResult(isLicensed(context))
    }
}
