package com.ghostmago.app.data

import android.content.Context
import com.ghostmago.app.crypto.CryptoEngine
import com.ghostmago.app.util.SecurityUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Encrypted local chat history for mesh
 */
object ChatHistory {
    private fun file(context: Context) = File(context.filesDir, "chat/history.enc")

    fun append(context: Context, groupId: String, nick: String, text: String, isMine: Boolean) {
        try {
            val dir = File(context.filesDir, "chat").apply { mkdirs() }
            val arr = loadRaw(context)
            arr.put(JSONObject().apply {
                put("g", groupId)
                put("n", nick)
                put("t", text)
                put("m", isMine)
                put("ts", System.currentTimeMillis())
            })
            // keep last 500
            while (arr.length() > 500) arr.remove(0)
            saveRaw(context, arr)
        } catch (_: Exception) {}
    }

    fun load(context: Context, groupId: String): List<Triple<String, String, Boolean>> {
        return try {
            val arr = loadRaw(context)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                if (o.optString("g") != groupId) null
                else Triple(o.optString("n"), o.optString("t"), o.optBoolean("m"))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun clear(context: Context) {
        try { file(context).delete() } catch (_: Exception) {}
    }

    private fun loadRaw(context: Context): JSONArray {
        val f = file(context)
        if (!f.exists()) return JSONArray()
        val pw = "history|" + (SecurityUtils.encryptedPrefs(context).getString("app_pw_hash", "x") ?: "x")
        val dec = CryptoEngine.decrypt(f.readBytes(), pw.toCharArray())
        return JSONArray(String(dec, Charsets.UTF_8))
    }

    private fun saveRaw(context: Context, arr: JSONArray) {
        val f = file(context)
        f.parentFile?.mkdirs()
        val pw = "history|" + (SecurityUtils.encryptedPrefs(context).getString("app_pw_hash", "x") ?: "x")
        f.writeBytes(CryptoEngine.encrypt(arr.toString().toByteArray(Charsets.UTF_8), pw.toCharArray()))
    }
}
