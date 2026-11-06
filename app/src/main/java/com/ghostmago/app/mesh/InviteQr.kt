package com.ghostmago.app.mesh

import android.graphics.Bitmap
import com.ghostmago.app.util.QrHelper
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Commander invite via QR + optional expiry (seconds).
 * Manual password join remains fully supported.
 */
object InviteQr {
    private const val PREFIX = "GMAGO1:"

    /** Default / last used TTL seconds (also stored in AppState.inviteTtlSec) */
    @Volatile var defaultTtlSec: Int = 3600 // 1 hour

    fun buildPayload(
        groupId: String,
        groupPassword: String,
        commanderNick: String,
        suggestedRole: String = "soldier",
        ttlSec: Int = defaultTtlSec
    ): String {
        val ts = System.currentTimeMillis() / 1000
        val ttl = ttlSec.coerceIn(60, 86400 * 7) // 1 min .. 7 days
        val body = JSONObject().apply {
            put("g", groupId.ifBlank { "default" })
            put("p", groupPassword)
            put("n", commanderNick)
            put("r", suggestedRole)
            put("t", ts)
            put("ttl", ttl)
        }.toString()
        val sig = MessageDigest.getInstance("SHA-256")
            .digest((body + "|ghostmago").toByteArray(Charsets.UTF_8))
            .take(4).joinToString("") { "%02x".format(it) }
        return PREFIX + body + "|" + sig
    }

    data class Invite(
        val groupId: String,
        val groupPassword: String,
        val commanderNick: String,
        val suggestedRole: String,
        val timestamp: Long,
        val ttlSec: Int
    )

    fun parse(raw: String): Invite? {
        return try {
            var s = raw.trim().replace("\r", "").replace("\uFEFF", "")
            // Allow payload without finding PREFIX at start (camera OCR noise)
            if (!s.startsWith(PREFIX)) {
                val idx = s.indexOf(PREFIX)
                if (idx >= 0) s = s.substring(idx) else return null
            }
            val rest = s.removePrefix(PREFIX)
            val pipe = rest.lastIndexOf('|')
            if (pipe <= 0) return null
            val body = rest.substring(0, pipe)
            val sig = rest.substring(pipe + 1)
            val expect = MessageDigest.getInstance("SHA-256")
                .digest((body + "|ghostmago").toByteArray(Charsets.UTF_8))
                .take(4).joinToString("") { "%02x".format(it) }
            if (!sig.equals(expect, ignoreCase = true)) return null
            val o = JSONObject(body)
            val ts = o.optLong("t", 0)
            val ttl = o.optInt("ttl", 86400)
            if (ts > 0) {
                val age = kotlin.math.abs(System.currentTimeMillis() / 1000 - ts)
                if (age > ttl) return null // expired
            }
            Invite(
                groupId = o.optString("g", "default"),
                groupPassword = o.optString("p", ""),
                commanderNick = o.optString("n", "فرمانده"),
                suggestedRole = o.optString("r", "soldier"),
                timestamp = ts,
                ttlSec = ttl
            )
        } catch (_: Exception) {
            null
        }
    }

    fun toBitmap(payload: String, size: Int = 512): Bitmap =
        QrHelper.generate(payload, size)

    fun ttlLabel(sec: Int): String = when {
        sec < 60 -> "$sec ثانیه"
        sec < 3600 -> "${sec / 60} دقیقه"
        sec < 86400 -> "${sec / 3600} ساعت"
        else -> "${sec / 86400} روز"
    }
}
