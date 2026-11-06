package com.ghostmago.app.security

import android.content.Context
import com.ghostmago.app.crypto.CryptoEngine
import org.json.JSONArray
import org.json.JSONObject

data class VaultEntry(val id: String, val title: String, val username: String, val secret: String, val note: String)

object PasswordVault {
    private const val PREF = "vault_enc_v1"
    private const val KEY = "blob"

    private fun masterKey(appPassHash: String) = "vault|$appPassHash|v1"

    fun list(context: Context, appPassHash: String): List<VaultEntry> {
        return try {
            val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
                ?: return emptyList()
            val plain = CryptoEngine.decryptString(raw, masterKey(appPassHash))
            val arr = JSONArray(plain)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                VaultEntry(
                    o.getString("id"),
                    o.optString("title"),
                    o.optString("user"),
                    o.optString("secret"),
                    o.optString("note")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAll(context: Context, appPassHash: String, entries: List<VaultEntry>) {
        val arr = JSONArray()
        entries.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("title", it.title)
                put("user", it.username)
                put("secret", it.secret)
                put("note", it.note)
            })
        }
        val enc = CryptoEngine.encryptString(arr.toString(), masterKey(appPassHash))
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, enc).apply()
    }

    fun add(context: Context, appPassHash: String, title: String, user: String, secret: String, note: String) {
        val list = list(context, appPassHash).toMutableList()
        list.add(
            VaultEntry(
                id = System.currentTimeMillis().toString(),
                title = title,
                username = user,
                secret = secret,
                note = note
            )
        )
        saveAll(context, appPassHash, list)
    }

    fun remove(context: Context, appPassHash: String, id: String) {
        saveAll(context, appPassHash, list(context, appPassHash).filter { it.id != id })
    }
}
