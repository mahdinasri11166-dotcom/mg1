package com.ghostmago.app

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ghostmago.app.data.AppState
import com.ghostmago.app.util.Attestation
import com.ghostmago.app.util.SecurityUtils
import java.io.File

class GhostMagoApp : Application() {
    companion object {
        lateinit var instance: GhostMagoApp
            private set

        fun securePrefs(context: Context) =
            EncryptedSharedPreferences.create(
                context,
                "ghostmago_secure",
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            com.ghostmago.app.util.LocaleHelper.applyToApp(this)
        } catch (_: Exception) {
        }

        // RASP delayed & low priority — must not slow first Activity frame
        Handler(Looper.getMainLooper()).postDelayed({
            Thread({
                try {
                    val report = Attestation.scan(this)
                    if (report.issues.isNotEmpty()) {
                        try {
                            SecurityUtils.audit(this, "RASP:" + report.issues.joinToString(","))
                        } catch (_: Exception) {
                        }
                    }
                    val hard = AppState.strictAttestation && !AppState.devMode
                    if (hard && (report.rooted || report.debugger || isClassicRootPaths())) {
                        Handler(Looper.getMainLooper()).post {
                            try {
                                Toast.makeText(
                                    this,
                                    "هشدار امنیتی: محیط مشکوک تشخیص داده شد",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (_: Exception) {
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }, "rasp-scan").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
                start()
            }
        }, 8000L)

        try {
            Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
                try {
                    android.util.Log.e("Mishkat", "Uncaught on ${thread.name}", ex)
                    SecurityUtils.audit(this, "CRASH:" + (ex.message ?: ex.javaClass.simpleName))
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun isClassicRootPaths(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su"
        )
        return paths.any { File(it).exists() }
    }
}
