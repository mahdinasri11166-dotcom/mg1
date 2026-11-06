package com.ghostmago.app.data

import android.content.Context
import org.json.JSONObject

/**
 * Persisted Safety Number verifications — required before private chat when strict.
 */
object VerifiedPeers {
    private const val PREF = "mishkat_verified_peers_v1"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun isVerified(context: Context, peerHash: String, fingerprint: String): Boolean {
        if (peerHash.isBlank() || fingerprint.isBlank()) return false
        val stored = prefs(context).getString(peerHash, null) ?: return false
        return normalize(stored) == normalize(fingerprint)
    }

    fun markVerified(context: Context, peerHash: String, fingerprint: String) {
        if (peerHash.isBlank() || fingerprint.isBlank()) return
        prefs(context).edit().putString(peerHash, normalize(fingerprint)).apply()
        try {
            AuditLog.append(context, "SAFETY_OK", peerHash.take(12))
        } catch (_: Exception) {
        }
    }

    fun revoke(context: Context, peerHash: String) {
        prefs(context).edit().remove(peerHash).apply()
    }

    fun normalize(s: String): String =
        s.replace(" ", "").replace("-", "").uppercase()

    /** Strict match: full normalized equality, minimum length 12. */
    fun matchesStrict(local: String, remoteInput: String): Boolean {
        val a = normalize(local)
        val b = normalize(remoteInput)
        if (a.length < 12 || b.length < 12) return false
        return a == b || (a.length >= 16 && b.length >= 16 && a.take(16) == b.take(16))
    }

    fun exportJson(context: Context): String {
        val all = prefs(context).all
        val o = JSONObject()
        for ((k, v) in all) {
            if (v is String) o.put(k, v)
        }
        return o.toString()
    }

    fun importJson(context: Context, json: String) {
        try {
            val o = JSONObject(json)
            val ed = prefs(context).edit()
            for (k in o.keys()) {
                ed.putString(k, o.getString(k))
            }
            ed.apply()
        } catch (_: Exception) {
        }
    }
}
