package com.ghostmago.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ChatContact(val hash: String, val nick: String)

object ChatContacts {
    private const val KEY = "chat_contacts_json"

    fun list(context: Context): List<ChatContact> {
        return try {
            val raw = context.getSharedPreferences("ghostmago_basic", Context.MODE_PRIVATE)
                .getString(KEY, "[]") ?: "[]"
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatContact(o.getString("hash"), o.optString("nick", "?"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, hash: String, nick: String) {
        val cur = list(context).filter { it.hash != hash }.toMutableList()
        cur.add(0, ChatContact(hash, nick.ifBlank { hash.take(8) }))
        val arr = JSONArray()
        cur.take(50).forEach {
            arr.put(JSONObject().put("hash", it.hash).put("nick", it.nick))
        }
        context.getSharedPreferences("ghostmago_basic", Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
        AuditLog.append(context, "CONTACT_SAVE", nick)
    }

    fun remove(context: Context, hash: String) {
        val cur = list(context).filter { it.hash != hash }
        val arr = JSONArray()
        cur.forEach { arr.put(JSONObject().put("hash", it.hash).put("nick", it.nick)) }
        context.getSharedPreferences("ghostmago_basic", Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
