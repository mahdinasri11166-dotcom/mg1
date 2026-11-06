package com.ghostmago.app.data

import android.content.Context
import com.ghostmago.app.crypto.CryptoEngine
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local encrypted audit trail for commander review.
 */
object AuditLog {
    private const val PREF = "audit_enc_v1"
    private const val KEY = "events"
    private const val MAX = 200

    fun append(context: Context, type: String, detail: String) {
        try {
            val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val raw = p.getString(KEY, null)
            val arr = if (raw.isNullOrBlank()) JSONArray() else {
                try {
                    val plain = CryptoEngine.decryptString(raw, sealKey(context))
                    JSONArray(plain)
                } catch (_: Exception) {
                    JSONArray()
                }
            }
            arr.put(JSONObject().apply {
                put("t", System.currentTimeMillis())
                put("type", type)
                put("detail", detail.take(200))
            })
            while (arr.length() > MAX) arr.remove(0)
            val enc = CryptoEngine.encryptString(arr.toString(), sealKey(context))
            p.edit().putString(KEY, enc).apply()
        } catch (_: Exception) {
        }
    }

    fun readAll(context: Context): List<Triple<Long, String, String>> {
        return try {
            val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val raw = p.getString(KEY, null) ?: return emptyList()
            val plain = CryptoEngine.decryptString(raw, sealKey(context))
            val arr = JSONArray(plain)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Triple(o.optLong("t"), o.optString("type"), o.optString("detail"))
            }.reversed()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun sealKey(context: Context): String {
        // Bound to app password hash material; not exported
        val h = AppState.appPasswordHash.ifBlank { "ghost-default" }
        return "audit|$h|v1"
    }
}
