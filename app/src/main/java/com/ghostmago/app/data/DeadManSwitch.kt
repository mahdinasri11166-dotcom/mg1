package com.ghostmago.app.data

import android.content.Context
import com.ghostmago.app.util.SecurityUtils

/**
 * Dead-man's switch ("کلید مرگ"):
 * If user does not check-in before deadline, a pre-armed action runs.
 */
object DeadManSwitch {
    private const val KEY_ENABLED = "dms_enabled"
    private const val KEY_DEADLINE = "dms_deadline"
    private const val KEY_INTERVAL = "dms_interval_min"
    private const val KEY_MSG = "dms_message"
    private const val KEY_FIRED = "dms_fired"
    private const val KEY_PENDING = "dms_pending_msg"

    fun isEnabled(ctx: Context): Boolean =
        SecurityUtils.encryptedPrefs(ctx).getBoolean(KEY_ENABLED, false)

    fun intervalMin(ctx: Context): Int =
        SecurityUtils.encryptedPrefs(ctx).getInt(KEY_INTERVAL, 60)

    fun message(ctx: Context): String =
        SecurityUtils.encryptedPrefs(ctx).getString(KEY_MSG, "CHECKIN_FAILED") ?: "CHECKIN_FAILED"

    fun deadline(ctx: Context): Long =
        SecurityUtils.encryptedPrefs(ctx).getLong(KEY_DEADLINE, 0L)

    fun enable(ctx: Context, intervalMinutes: Int, msg: String) {
        val now = System.currentTimeMillis()
        val iv = intervalMinutes.coerceIn(5, 60 * 24 * 7)
        SecurityUtils.encryptedPrefs(ctx).edit()
            .putBoolean(KEY_ENABLED, true)
            .putInt(KEY_INTERVAL, iv)
            .putString(KEY_MSG, msg.ifBlank { "CHECKIN_FAILED" })
            .putLong(KEY_DEADLINE, now + iv * 60_000L)
            .putBoolean(KEY_FIRED, false)
            .apply()
        SecurityUtils.audit(ctx, "DMS_ENABLED_${iv}m")
    }

    fun disable(ctx: Context) {
        SecurityUtils.encryptedPrefs(ctx).edit()
            .putBoolean(KEY_ENABLED, false)
            .putBoolean(KEY_FIRED, false)
            .apply()
        SecurityUtils.audit(ctx, "DMS_DISABLED")
    }

    /** Call on user activity / open app */
    fun checkIn(ctx: Context) {
        if (!isEnabled(ctx)) return
        val iv = intervalMin(ctx)
        SecurityUtils.encryptedPrefs(ctx).edit()
            .putLong(KEY_DEADLINE, System.currentTimeMillis() + iv * 60_000L)
            .putBoolean(KEY_FIRED, false)
            .apply()
        SecurityUtils.audit(ctx, "DMS_CHECKIN")
    }

    /**
     * @return message to broadcast if fired, else null
     */
    fun poll(ctx: Context): String? {
        if (!isEnabled(ctx)) return null
        val p = SecurityUtils.encryptedPrefs(ctx)
        if (p.getBoolean(KEY_FIRED, false)) return null
        val dl = p.getLong(KEY_DEADLINE, 0L)
        if (dl > 0 && System.currentTimeMillis() > dl) {
            val msg = message(ctx)
            p.edit()
                .putBoolean(KEY_FIRED, true)
                .putString(KEY_PENDING, msg)
                .apply()
            SecurityUtils.audit(ctx, "DMS_FIRED")
            return msg
        }
        return null
    }

    fun consumePendingBroadcast(ctx: Context): String? {
        val p = SecurityUtils.encryptedPrefs(ctx)
        val m = p.getString(KEY_PENDING, null) ?: return null
        p.edit().remove(KEY_PENDING).apply()
        return m
    }

    fun remainingMs(ctx: Context): Long {
        if (!isEnabled(ctx)) return -1
        return (deadline(ctx) - System.currentTimeMillis()).coerceAtLeast(0)
    }
}
