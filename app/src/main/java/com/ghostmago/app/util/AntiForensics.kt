package com.ghostmago.app.util

import android.content.Context
import android.os.Build
import java.io.File

object AntiForensics {
    fun clearClipboard(context: Context) {
        SecurityUtils.copyAndClear(context, "", "", 0)
    }

    fun isRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
            "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su"
        )
        if (paths.any { File(it).exists() }) return true
        return try {
            Runtime.getRuntime().exec(arrayOf("which", "su")).inputStream.bufferedReader().readLine() != null
        } catch (_: Exception) { false }
    }

    fun isDebuggerAttached(): Boolean =
        android.os.Debug.isDebuggerConnected()

    fun securityReport(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("Root: ${if (isRooted()) "⚠️ بله" else "✅ خیر"}")
        sb.appendLine("Debugger: ${if (isDebuggerAttached()) "⚠️ متصل" else "✅ خیر"}")
        sb.appendLine("SDK: ${Build.VERSION.SDK_INT}")
        sb.appendLine("Model: ${Build.MODEL}")
        return sb.toString()
    }
}
