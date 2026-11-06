package com.ghostmago.app.data

import android.content.Context
import com.ghostmago.app.crypto.CryptoEngine
import com.ghostmago.app.util.SecurityUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Separate encrypted store for duress/honey mode.
 * Real vault: ghostmago_vault prefs + filesDir/chat
 * Honey vault: filesDir/honey/store.enc — never mixed with real data.
 */
object HoneyStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "honey/store.enc")

    private fun key(ctx: Context): CharArray {
        // independent of real app password
        val seed = SecurityUtils.encryptedPrefs(ctx).getString("honey_seed", null)
            ?: run {
                val s = java.util.UUID.randomUUID().toString()
                SecurityUtils.encryptedPrefs(ctx).edit().putString("honey_seed", s).apply()
                s
            }
        return ("honey|" + seed).toCharArray()
    }

    fun ensureDefaults(ctx: Context) {
        if (file(ctx).exists()) return
        val arr = JSONArray()
        AppState.honeyMessages().forEach { (n, m) ->
            arr.put(JSONObject().apply {
                put("n", n); put("t", m); put("ts", System.currentTimeMillis())
            })
        }
        save(ctx, arr)
    }

    fun loadMessages(ctx: Context): List<Pair<String, String>> {
        ensureDefaults(ctx)
        return try {
            val arr = load(ctx)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                o.optString("n") to o.optString("t")
            }
        } catch (_: Exception) {
            AppState.honeyMessages()
        }
    }

    fun append(ctx: Context, nick: String, text: String) {
        val arr = try { load(ctx) } catch (_: Exception) { JSONArray() }
        arr.put(JSONObject().apply {
            put("n", nick); put("t", text); put("ts", System.currentTimeMillis())
        })
        while (arr.length() > 50) arr.remove(0)
        save(ctx, arr)
    }

    /** Panic must wipe honey too, but separately from real paths */
    fun wipe(ctx: Context) {
        try {
            val f = file(ctx)
            if (f.exists()) SecurityUtils.secureWipe(f)
            f.parentFile?.deleteRecursively()
        } catch (_: Exception) {}
    }

    private fun load(ctx: Context): JSONArray {
        val f = file(ctx)
        if (!f.exists()) return JSONArray()
        val dec = CryptoEngine.decrypt(f.readBytes(), key(ctx))
        return JSONArray(String(dec, Charsets.UTF_8))
    }

    private fun save(ctx: Context, arr: JSONArray) {
        val f = file(ctx)
        f.parentFile?.mkdirs()
        f.writeBytes(CryptoEngine.encrypt(arr.toString().toByteArray(Charsets.UTF_8), key(ctx)))
    }
}
