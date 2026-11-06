package com.ghostmago.app.crypto

import android.content.Context
import com.ghostmago.app.data.AppState

/**
 * Organizational crypto policy for مشکات.
 * - Private chat: X3DH + Double Ratchet preferred / required
 * - Group: epoch-rotated session keys
 * - Hardware keystore when available
 * - Shamir multi-party for master backup
 * - Short calculator PIN is decoy only — not primary auth strength
 */
object CryptoPolicy {
    const val MIN_APP_PASSWORD = 8
    const val MIN_GROUP_PASSWORD = 8
    const val MIN_BACKUP_PASSWORD = 10
    const val SHAMIR_N = 5
    const val SHAMIR_K = 3
    const val GROUP_EPOCH_MAX_AGE_MS = 6 * 60 * 60 * 1000L // rotate hint every 6h

    fun requireX3dhPrivate(): Boolean = AppState.requireX3dhPrivate

    fun allowSymmetricPrivateFallback(): Boolean = !AppState.requireX3dhPrivate

    fun enforceHardwareKeystore(): Boolean = AppState.forceHardwareKeystore

    fun validateAppPassword(pw: String): String? {
        if (pw.length < MIN_APP_PASSWORD) return "رمز برنامه حداقل $MIN_APP_PASSWORD کاراکتر"
        if (pw.all { it.isDigit() } && pw.length < 12) return "رمز فقطعددی ضعیف است — ترکیب حرف و عدد"
        return null
    }

    fun validateGroupPassword(pw: String): String? {
        if (pw.length < MIN_GROUP_PASSWORD) return "رمز گروه حداقل $MIN_GROUP_PASSWORD کاراکتر"
        return null
    }

    fun pinIsDecoyOnly(): Boolean = true

    fun hardwareStatus(context: Context): String =
        HardwareKeystore.status(context)
}
