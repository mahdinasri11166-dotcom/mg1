package com.ghostmago.app.util

/**
 * Drop-or-coalesce high-frequency UI status updates to reduce jank.
 */
class UiThrottle(private val minIntervalMs: Long = 600L) {
    @Volatile private var lastAt = 0L
    @Volatile private var lastMsg = ""

    /** Returns true if the message should be shown now. */
    fun accept(msg: String): Boolean {
        val now = System.currentTimeMillis()
        if (msg == lastMsg && now - lastAt < minIntervalMs * 2) return false
        if (now - lastAt < minIntervalMs) return false
        lastAt = now
        lastMsg = msg
        return true
    }
}
