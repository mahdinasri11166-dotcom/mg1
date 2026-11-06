package com.ghostmago.app.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

data class SecurityFinding(
    val level: String, // critical / warn / info
    val title: String,
    val detail: String
)

object IntrusionMonitor {

    private val SUSPICIOUS_PACKAGES = listOf(
        "com.noshufou.android.su",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.thirdparty.superuser",
        "com.yellowes.su",
        "com.topjohnwu.magisk",
        "io.github.huskydg.magisk",
        "com.kingroot.kinguser",
        "com.kingo.root",
        "com.smedialink.oneclickroot",
        "com.zhiqupk.root.global",
        "com.alephzain.framaroot",
        "de.robv.android.xposed.installer",
        "com.saurik.substrate",
        "com.android.vending.billing.InAppBillingService.COIN",
        "com.chelpus.lackypatch",
        "com.ramdroid.appquarantine"
    )

    private val SUSPICIOUS_NAME_HINTS = listOf(
        "keylogger", "spy", "track", "stalker", "monitor parent", "hidden cam",
        "malware", "trojan", "root cloak"
    )

    fun scan(context: Context): List<SecurityFinding> {
        val out = mutableListOf<SecurityFinding>()
        val pm = context.packageManager

        // Root paths
        val rootPaths = listOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su"
        )
        val rootHit = rootPaths.any { File(it).exists() }
        if (rootHit) {
            out += SecurityFinding("critical", "نشانههای روت", "مسیرهای شناختهشده su روی دستگاه دیده شد.")
        }

        // Magisk / test-keys
        try {
            val tags = Build.TAGS ?: ""
            if (tags.contains("test-keys")) {
                out += SecurityFinding("warn", "firmware تستی", "Build.TAGS شامل test-keys است.")
            }
        } catch (_: Exception) {
        }

        // Emulator hints
        val brand = (Build.BRAND + Build.DEVICE + Build.MODEL + Build.PRODUCT).lowercase()
        if (listOf("generic", "emulator", "google_sdk", "sdk_gphone", "genymotion").any { brand.contains(it) }) {
            out += SecurityFinding("warn", "احتمال شبیهساز", "شناسههای سختافزاری شبیه امولاتور است.")
        }

        // Installed packages
        try {
            val apps = if (Build.VERSION.SDK_INT >= 33) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            for (ai in apps) {
                val pkg = ai.packageName
                if (SUSPICIOUS_PACKAGES.any { pkg.equals(it, true) || pkg.startsWith(it) }) {
                    out += SecurityFinding("critical", "اپ مشکوک نصبشده", pkg)
                }
                val label = try {
                    pm.getApplicationLabel(ai).toString().lowercase()
                } catch (_: Exception) {
                    ""
                }
                if (SUSPICIOUS_NAME_HINTS.any { label.contains(it) || pkg.lowercase().contains(it) }) {
                    out += SecurityFinding("warn", "نام مشکوک اپ", "$label ($pkg)")
                }
                // Debuggable third-party
                if ((ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
                    (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                ) {
                    out += SecurityFinding("info", "اپ قابل دیباگ", pkg)
                }
            }
        } catch (e: Exception) {
            out += SecurityFinding("info", "اسکن بسته محدود", e.message ?: "بدون دسترسی کامل لیست اپها")
        }

        // Accessibility services (potential overlay/key capture)
        try {
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            if (enabled.isNotBlank()) {
                out += SecurityFinding(
                    "warn",
                    "سرویس دسترسپذیری فعال",
                    enabled.take(200)
                )
            }
        } catch (_: Exception) {
        }

        if (out.isEmpty()) {
            out += SecurityFinding("info", "نتیجه", "مورد بحرانی شناختهشدهای یافت نشد. این جایگزین آنتیویروس کامل نیست.")
        }
        return out.distinctBy { it.title + it.detail }
    }
}
