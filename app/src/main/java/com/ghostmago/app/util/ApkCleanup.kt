package com.ghostmago.app.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Best-effort: remove leftover installer APKs after first run
 * so the install package is harder to re-share from Files.
 * Android 10+ scoped storage limits what we can delete.
 */
object ApkCleanup {
    private const val PREF = "apk_cleanup"
    private const val KEY_DONE = "done_v1"

    fun runOnce(context: Context) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (sp.getBoolean(KEY_DONE, false)) return
        try {
            deleteViaMediaStore(context)
            deleteCommonPaths(context)
        } catch (_: Exception) {
        } finally {
            sp.edit().putBoolean(KEY_DONE, true).apply()
        }
    }

    private fun deleteViaMediaStore(context: Context) {
        if (Build.VERSION.SDK_INT < 29) return
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME)
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("%.apk", "%GhostMago%")
            context.contentResolver.query(uri, projection, selection, args, null)?.use { c ->
                val idIdx = c.getColumnIndex(MediaStore.Files.FileColumns._ID)
                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val row = android.content.ContentUris.withAppendedId(uri, id)
                    try {
                        context.contentResolver.delete(row, null, null)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun deleteCommonPaths(context: Context) {
        val names = listOf("GhostMago", "ghostmago", "روح", "Calculator")
        val dirs = mutableListOf<File>()
        try {
            dirs += Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } catch (_: Exception) {
        }
        context.getExternalFilesDir(null)?.parentFile?.let { dirs += it }
        context.cacheDir?.let { dirs += it }
        context.filesDir?.let { dirs += it }
        for (dir in dirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.endsWith(".apk", true)) {
                    val n = f.name.lowercase()
                    if (names.any { n.contains(it.lowercase()) } || n.contains("mago") || n.contains("ghost")) {
                        try {
                            f.delete()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }
}
