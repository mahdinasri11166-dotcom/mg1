package com.ghostmago.app.util

import com.ghostmago.app.data.AppState

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.Inflater

object SecurityUtils {

    @Volatile private var cachedPrefs: SharedPreferences? = null
    private val prefsLock = Any()

    /** Cached EncryptedSharedPreferences — MasterKey is expensive; never rebuild per call. */
    fun encryptedPrefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        synchronized(prefsLock) {
            cachedPrefs?.let { return it }
            val app = context.applicationContext
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val p = EncryptedSharedPreferences.create(
                app, "ghostmago_vault", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            cachedPrefs = p
            return p
        }
    }

    fun secureWipe(file: File, passes: Int = 3) {
        if (!file.exists()) return
        try {
            val size = file.length()
            if (size > 0) {
                val random = SecureRandom()
                file.outputStream().use { out ->
                    val buf = ByteArray(4096)
                    repeat(passes) {
                        var remaining = size
                        while (remaining > 0) {
                            val n = minOf(buf.size.toLong(), remaining).toInt()
                            random.nextBytes(buf)
                            out.write(buf, 0, n)
                            remaining -= n
                        }
                        out.flush()
                    }
                }
            }
            file.delete()
        } catch (_: Exception) {
            try { file.delete() } catch (_: Exception) {}
        }
    }

    fun panicWipe(context: Context) {
        try {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancelAll()
            } catch (_: Exception) {}

            // Clear encrypted prefs / stats / audit inside SecurityUtils
            try {
                encryptedPrefs(context).edit().clear().apply()
            } catch (_: Exception) {}

            // App SharedPreferences
            try {
                context.getSharedPreferences("ghostmago_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                context.getSharedPreferences("ghostmago_basic", Context.MODE_PRIVATE).edit().clear().apply()
                context.getSharedPreferences("audit_enc_v1", Context.MODE_PRIVATE).edit().clear().apply()
                context.getSharedPreferences("vault_enc_v1", Context.MODE_PRIVATE).edit().clear().apply()
                context.getSharedPreferences("integrity_v1", Context.MODE_PRIVATE).edit().clear().apply()
                context.getSharedPreferences("apk_cleanup", Context.MODE_PRIVATE).edit().clear().apply()
            } catch (_: Exception) {}

            // Delete app files dirs
            val dirs = listOf(
                File(context.filesDir, "stego"),
                File(context.filesDir, "qr"),
                File(context.filesDir, "filecrypto"),
                File(context.filesDir, "integrity"),
                File(context.filesDir, "bg"),
                File(context.cacheDir, "share"),
                File(context.cacheDir, "wipe"),
                File(context.cacheDir, "audio"),
                context.cacheDir,
                context.filesDir
            )
            for (d in dirs) {
                try {
                    d.listFiles()?.forEach { f ->
                        try {
                            if (f.isFile) {
                                // quick overwrite small files
                                if (f.length() in 1..2_000_000) {
                                    f.writeBytes(ByteArray(f.length().toInt()) { 0 })
                                }
                                f.delete()
                            } else if (f.isDirectory) {
                                f.deleteRecursively()
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }

            try { com.ghostmago.app.data.HoneyStore.wipe(context) } catch (_: Exception) {}
            try {
                // Reset in-memory state
                AppState.isLoggedIn = false
                AppState.isHoneyMode = false
                AppState.isDuressMode = false
                AppState.roomsJson = "[]"
                AppState.groupId = "default"
                AppState.nickname = "user"
                AppState.save(context)
            } catch (_: Exception) {}

            audit(context, "PANIC_WIPE_DONE")
        } catch (_: Exception) {}
    }

    fun saveStat(context: Context, key: String, increment: Int = 1) {
        val p = encryptedPrefs(context)
        p.edit().putInt(key, p.getInt(key, 0) + increment).apply()
    }

    fun getStat(context: Context, key: String): Int = encryptedPrefs(context).getInt(key, 0)

    fun audit(context: Context, event: String) {
        try {
            val p = encryptedPrefs(context)
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val line = "$ts | $event"
            val prev = p.getString("audit_log", "") ?: ""
            val lines = (prev.lines() + line).takeLast(100)
            p.edit().putString("audit_log", lines.joinToString("\n")).apply()
        } catch (_: Exception) {}
    }

    fun getAuditLog(context: Context): String =
        encryptedPrefs(context).getString("audit_log", "") ?: ""

    fun copyAndClear(context: Context, label: String, text: String, delayMs: Long = 30_000) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Handler(Looper.getMainLooper()).postDelayed({
            try { cm.setPrimaryClip(ClipData.newPlainText("", "")) } catch (_: Exception) {}
        }, delayMs)
    }

    fun compress(data: ByteArray): ByteArray {
        val d = Deflater(9)
        d.setInput(data); d.finish()
        val out = ByteArray(data.size + 64)
        val n = d.deflate(out); d.end()
        return out.copyOf(n)
    }

    fun decompress(data: ByteArray): ByteArray {
        val inf = Inflater()
        inf.setInput(data)
        val out = ByteArray(data.size * 4 + 1024)
        val n = inf.inflate(out); inf.end()
        return out.copyOf(n)
    }
}
