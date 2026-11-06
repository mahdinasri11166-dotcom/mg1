package com.ghostmago.app.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Harder anti-root / anti-tamper checks. Used by DeviceGuard.
 */
object Attestation {
    data class Report(
        val rooted: Boolean,
        val debugger: Boolean,
        val emulator: Boolean,
        val debuggable: Boolean,
        val issues: List<String>
    ) {
        val risky get() = rooted || debugger || emulator || debuggable
    }

    fun scan(context: Context): Report {
        val issues = mutableListOf<String>()
        val rooted = (AntiForensics.isRooted() || hasSuBinary() || hasMagisk()).also {
            if (it) issues.add("root/su")
        }
        val debugger = AntiForensics.isDebuggerAttached().also { if (it) issues.add("debugger") }
        val emulator = isEmulator().also { if (it) issues.add("emulator") }
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) issues.add("debuggable")
        listOf("/sbin/.magisk", "/data/adb/magisk", "/system/xbin/daemonsu").forEach {
            if (File(it).exists()) issues.add("magisk-path")
        }
        if (isTestKeys()) issues.add("test-keys")
        return Report(rooted, debugger, emulator, debuggable, issues.distinct())
    }

    private fun hasSuBinary(): Boolean {
        val paths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/sd/xbin/su", "/data/local/xbin/su", "/data/local/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    private fun hasMagisk(): Boolean =
        File("/data/adb/magisk").exists() || File("/sbin/.magisk").exists()

    private fun isTestKeys(): Boolean =
        Build.TAGS?.contains("test-keys") == true

    private fun isEmulator(): Boolean {
        val f = Build.FINGERPRINT
        return f.startsWith("generic") || f.contains("emulator") ||
            Build.MODEL.contains("Emulator", true) ||
            Build.MANUFACTURER.contains("Genymotion", true) ||
            Build.PRODUCT.contains("sdk") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.BOARD.contains("unknown", true) && Build.BRAND.startsWith("generic")
    }

    /** Soft Play Integrity placeholder — real Integrity API needs cloud project; local signals only. */
    fun localIntegrityHint(context: Context): String {
        val r = scan(context)
        return when {
            r.rooted -> "INTEGRITY_FAIL_ROOT"
            r.emulator -> "INTEGRITY_FAIL_EMULATOR"
            r.debugger -> "INTEGRITY_FAIL_DEBUG"
            r.debuggable -> "INTEGRITY_WARN_DEBUGGABLE"
            else -> "INTEGRITY_LOCAL_OK"
        }
    }
}
