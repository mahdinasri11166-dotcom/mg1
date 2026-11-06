package com.ghostmago.app.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.ghostmago.app.data.AppState
import org.json.JSONArray
import org.json.JSONObject

/**
 * Organizational device posture & anti-tamper for مشکات.
 *
 * 1) Approved device whitelist (model / API / patch)
 * 2) Strong attestation mode
 * 3) Root / debugger / emulator / overlay / accessibility abuse
 * 4) Reaction: warn | readOnly | disconnect mesh
 * 5) Kiosk operational mode flag (UI enforces)
 */
object DeviceGuard {

    enum class Reaction { NONE, WARN, READ_ONLY, DISCONNECT }

    data class Posture(
        val approved: Boolean,
        val issues: List<String>,
        val reaction: Reaction,
        val detail: String
    )

    private const val PREF = "mishkat_device_guard_v1"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ─── 1) Device whitelist ─────────────────────────────
    fun deviceFingerprint(): String {
        val patch = if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "?"
        return listOf(
            Build.MANUFACTURER,
            Build.MODEL,
            "API${Build.VERSION.SDK_INT}",
            patch
        ).joinToString("|")
    }

    fun whitelistJson(context: Context): String =
        prefs(context).getString("whitelist", "[]") ?: "[]"

    fun setWhitelist(context: Context, entries: List<String>) {
        val arr = JSONArray()
        entries.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { arr.put(it) }
        prefs(context).edit().putString("whitelist", arr.toString()).apply()
    }

    fun addCurrentDeviceToWhitelist(context: Context) {
        val cur = whitelistEntries(context).toMutableList()
        val fp = deviceFingerprint()
        if (fp !in cur) cur.add(fp)
        setWhitelist(context, cur)
    }

    fun whitelistEntries(context: Context): List<String> {
        return try {
            val arr = JSONArray(whitelistJson(context))
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isDeviceApproved(context: Context): Boolean {
        if (!AppState.enforceDeviceWhitelist) return true
        val list = whitelistEntries(context)
        if (list.isEmpty()) return false // enforce with empty list = lock until commander adds
        val fp = deviceFingerprint()
        // match full or MODEL-only entries
        return list.any { entry ->
            entry == fp ||
                entry.equals(Build.MODEL, true) ||
                fp.contains(entry, ignoreCase = true)
        }
    }

    // ─── 2+3) Attestation + extra sensors ─────────────────
    fun hasOverlayPermission(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(context) else false
        } catch (_: Exception) {
            false
        }
    }

    fun enabledAccessibilityServices(context: Context): List<String> {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun suspiciousAccessibility(context: Context): Boolean {
        val services = enabledAccessibilityServices(context)
        // Flag when many services or unknown — soft heuristic
        return services.size >= 4
    }

    fun scanPosture(context: Context): Posture {
        val issues = mutableListOf<String>()
        if (!AppState.orgDeviceGuard && !AppState.strictAttestation && !AppState.enforceDeviceWhitelist) {
            return Posture(true, emptyList(), Reaction.NONE, "پاک")
        }
        val report = Attestation.scan(context)
        issues.addAll(report.issues)

        if (AppState.enforceDeviceWhitelist && !isDeviceApproved(context)) {
            issues.add("device-not-whitelisted")
        }
        if (hasOverlayPermission(context) && AppState.strictAttestation) {
            issues.add("overlay-permission")
        }
        if (AppState.strictAttestation && suspiciousAccessibility(context)) {
            issues.add("many-accessibility")
        }
        // USB debugging heuristic
        try {
            val adb = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
            if (adb && AppState.strictAttestation) issues.add("adb-enabled")
        } catch (_: Exception) {
        }

        val reaction = when {
            !AppState.orgDeviceGuard -> Reaction.NONE
            "device-not-whitelisted" in issues && AppState.enforceDeviceWhitelist ->
                reactionFromPolicy(AppState.deviceGuardReaction)
            report.rooted || report.debugger -> reactionFromPolicy(AppState.deviceGuardReaction)
            issues.isNotEmpty() && AppState.strictAttestation -> {
                val soft = AppState.deviceGuardReaction
                if (soft == "disconnect") Reaction.WARN else reactionFromPolicy(soft)
            }
            else -> Reaction.NONE
        }

        // Apply soft side-effects
        when (reaction) {
            Reaction.READ_ONLY -> AppState.forceReadOnly = true
            Reaction.DISCONNECT, Reaction.WARN, Reaction.NONE -> { /* mesh handled by caller */ }
        }

        return Posture(
            approved = "device-not-whitelisted" !in issues && !report.rooted,
            issues = issues.distinct(),
            reaction = reaction,
            detail = issues.joinToString(", ").ifBlank { "پاک" }
        )
    }

    private fun reactionFromPolicy(s: String): Reaction = when (s) {
        "readonly" -> Reaction.READ_ONLY
        "disconnect" -> Reaction.DISCONNECT
        "warn" -> Reaction.WARN
        else -> Reaction.WARN
    }

    fun applyReaction(context: Context, posture: Posture, stopMesh: (() -> Unit)?) {
        when (posture.reaction) {
            Reaction.READ_ONLY -> {
                AppState.forceReadOnly = true
                AppState.save(context)
            }
            Reaction.DISCONNECT -> {
                AppState.forceReadOnly = true
                try { stopMesh?.invoke() } catch (_: Exception) {}
                AppState.save(context)
            }
            Reaction.WARN, Reaction.NONE -> {}
        }
        try {
            SecurityUtils.audit(
                context,
                "DEVICE_GUARD:${posture.reaction}:${posture.detail.take(80)}"
            )
        } catch (_: Exception) {
        }
    }

    // ─── Kiosk ───────────────────────────────────────────
    fun kioskAllowedRoutes(): Set<String> = setOf(
        "calculator", "login", "onboarding", "home", "mesh",
        "send_stego", "read_stego", "sms_stego", "image_stego",
        "audio_stego", "file_stego", "qr", "qr_scan", "settings", "about", "compliance"
    )

    fun isKioskBlocked(route: String): Boolean {
        if (!AppState.kioskMode) return false
        return route !in kioskAllowedRoutes()
    }
}
