package com.ghostmago.app.security

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object FileIntegrity {
    private const val PREF = "integrity_v1"

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun watch(context: Context, path: String, label: String) {
        val f = File(path)
        require(f.exists()) { "فایل وجود ندارد" }
        val hash = sha256(f)
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val all = JSONObject(p.getString("map", "{}") ?: "{}")
        all.put(path, JSONObject().put("label", label).put("hash", hash).put("t", System.currentTimeMillis()))
        p.edit().putString("map", all.toString()).apply()
    }

    data class CheckResult(val path: String, val label: String, val ok: Boolean, val message: String)

    fun verifyAll(context: Context): List<CheckResult> {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val all = JSONObject(p.getString("map", "{}") ?: "{}")
        val keys = all.keys()
        val out = mutableListOf<CheckResult>()
        while (keys.hasNext()) {
            val path = keys.next()
            val o = all.getJSONObject(path)
            val label = o.optString("label", path)
            val expect = o.optString("hash")
            val f = File(path)
            if (!f.exists()) {
                out += CheckResult(path, label, false, "فایل حذف شده")
            } else {
                val now = sha256(f)
                out += if (now == expect) CheckResult(path, label, true, "سالم")
                else CheckResult(path, label, false, "تغییر کرده!")
            }
        }
        return out
    }
}
