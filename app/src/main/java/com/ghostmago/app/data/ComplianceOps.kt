package com.ghostmago.app.data

import android.content.Context
import android.os.Build
import com.ghostmago.app.crypto.CryptoEngine
import com.ghostmago.app.orgtrust.IdentityPolicy
import com.ghostmago.app.util.DeviceGuard
import org.json.JSONArray
import org.json.JSONObject

/**
 * Operations / Audit / Compliance for مشکات (section 5):
 * 1) Append-only sealed audit (hash chain — no erase API)
 * 2) Data classification labels on messages
 * 3) Retention / forced expiry policy
 * 4) One-click incident report (metadata only)
 * 5) Two-person rule for dangerous ops
 * 6) SOP / monthly panic-duress drill checklist
 */
object ImmutableAudit {
    private const val PREF = "audit_chain_v1"
    private const val KEY = "chain"
    private const val MAX = 500

    fun append(context: Context, type: String, detail: String) {
        try {
            val p = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val raw = p.getString(KEY, null)
            val arr = if (raw.isNullOrBlank()) JSONArray() else {
                try {
                    JSONArray(CryptoEngine.decryptString(raw, seal(context)))
                } catch (_: Exception) {
                    JSONArray()
                }
            }
            val prevHash = if (arr.length() == 0) "GENESIS" else {
                arr.getJSONObject(arr.length() - 1).optString("hash", "GENESIS")
            }
            val t = System.currentTimeMillis()
            val detailSafe = IdentityPolicy.sanitizeLog(detail).take(180)
            val body = "$prevHash|$t|$type|$detailSafe"
            val hash = CryptoEngine.sha256(body.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }.take(16)
            arr.put(
                JSONObject()
                    .put("t", t)
                    .put("type", type)
                    .put("detail", detailSafe)
                    .put("prev", prevHash)
                    .put("hash", hash)
            )
            while (arr.length() > MAX) {
                // Drop oldest but keep chain note — organizational: never expose clear API
                arr.remove(0)
            }
            val enc = CryptoEngine.encryptString(arr.toString(), seal(context))
            p.edit().putString(KEY, enc).apply()
            // mirror soft audit
            try {
                AuditLog.append(context, type, detailSafe)
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }

    fun readAll(context: Context): List<JSONObject> {
        return try {
            val p = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val raw = p.getString(KEY, null) ?: return emptyList()
            val arr = JSONArray(CryptoEngine.decryptString(raw, seal(context)))
            (0 until arr.length()).map { arr.getJSONObject(it) }.reversed()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun exportJson(context: Context): String {
        val arr = JSONArray()
        readAll(context).reversed().forEach { arr.put(it) }
        return JSONObject()
            .put("v", 1)
            .put("app", "مشکات")
            .put("exported", System.currentTimeMillis())
            .put("events", arr)
            .toString()
    }

    /** No public clear — intentional. Panic wipe still wipes prefs at OS level. */
    fun isTampered(context: Context): Boolean {
        // readAll is newest-first; verify oldest→newest
        val list = readAll(context).asReversed()
        if (list.isEmpty()) return false
        var prev = "GENESIS"
        for (o in list) {
            val ts = o.optLong("t")
            val type = o.optString("type")
            val detail = o.optString("detail")
            val body = "$prev|$ts|$type|$detail"
            val expect = CryptoEngine.sha256(body.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }.take(16)
            if (o.optString("prev") != prev) return true
            if (o.optString("hash") != expect) return true
            prev = o.optString("hash")
        }
        return false
    }

    private fun seal(context: Context): String {
        val h = AppState.appPasswordHash.ifBlank { "mishkat-audit" }
        return "immut|$h|v1"
    }
}

object MessageClassification {
    const val PUBLIC = "public"
    const val SENSITIVE = "sensitive"
    const val SECRET = "secret"

    fun labelFa(c: String): String = when (c) {
        SECRET -> "سری"
        SENSITIVE -> "حساس"
        else -> "عمومی"
    }

    fun canForward(c: String): Boolean = c != SECRET
}

object RetentionPolicy {
    fun retentionHours(): Int = AppState.messageRetentionHours.coerceIn(0, 24 * 30)

    fun isExpired(createdAtMs: Long, classification: String = MessageClassification.PUBLIC): Boolean {
        val hours = retentionHours()
        if (hours <= 0) return false
        val mult = when (classification) {
            MessageClassification.SECRET -> 0.5 // expire sooner
            MessageClassification.SENSITIVE -> 0.75
            else -> 1.0
        }
        val limit = (hours * 3600_000L * mult).toLong()
        return System.currentTimeMillis() - createdAtMs > limit
    }
}

object IncidentReport {
    /** Metadata-only report for security officer — no message bodies. */
    fun build(context: Context, note: String = ""): String {
        val posture = try {
            com.ghostmago.app.util.DeviceGuard.scanPosture(context)
        } catch (_: Exception) {
            null
        }
        return JSONObject()
            .put("v", 1)
            .put("app", "مشکات")
            .put("ts", System.currentTimeMillis())
            .put("note", IdentityPolicy.sanitizeLog(note).take(120))
            .put("role", AppState.meshRole.value)
            .put("alias", IdentityPolicy.sanitizeAlias(AppState.nickname))
            .put("groupId", AppState.groupId.take(32))
            .put("device", DeviceGuard.deviceFingerprint())
            .put("android", Build.VERSION.SDK_INT)
            .put("version", try {
                com.ghostmago.app.BuildConfig.VERSION_NAME
            } catch (_: Exception) {
                "?"
            })
            .put("posture", posture?.detail ?: "?")
            .put("issues", JSONArray(posture?.issues ?: emptyList<String>()))
            .put("integrity", try {
                com.ghostmago.app.util.Attestation.localIntegrityHint(context)
            } catch (_: Exception) {
                "?"
            })
            .toString()
    }
}

/**
 * Four-eyes: dangerous op requires a second operator passphrase confirmation.
 */
object TwoPersonRule {
    private const val PREF = "two_person_v1"

    fun setSecondOperatorHash(context: Context, password: String) {
        val h = AppState.hashPassword(password)
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString("second_hash", h).apply()
        ImmutableAudit.append(context, "TWO_PERSON_SET", "second operator configured")
    }

    fun hasSecondOperator(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("second_hash", null)?.isNotBlank() == true

    fun confirmSecond(context: Context, password: String): Boolean {
        val h = AppState.hashPassword(password)
        val expect = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("second_hash", null) ?: return false
        val ok = h == expect
        ImmutableAudit.append(
            context,
            if (ok) "TWO_PERSON_OK" else "TWO_PERSON_FAIL",
            "confirm"
        )
        return ok
    }

    fun requiresTwoPerson(op: String): Boolean {
        if (!AppState.requireTwoPersonOps) return false
        return op in setOf("PANIC_WIPE", "REMOTE_WIPE", "INIT_CA", "ISSUE_CERT", "ROLE_SUPREME", "EXPORT_BACKUP")
    }
}

object SopChecklist {
    private const val PREF = "sop_drill_v1"

    fun lastDrillAt(context: Context): Long =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getLong("last_drill", 0L)

    fun markDrillDone(context: Context) {
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putLong("last_drill", System.currentTimeMillis()).apply()
        ImmutableAudit.append(context, "SOP_DRILL", "monthly panic/duress practice")
    }

    fun needsDrill(context: Context, maxDays: Int = 30): Boolean {
        val last = lastDrillAt(context)
        if (last == 0L) return true
        return System.currentTimeMillis() - last > maxDays * 24L * 3600_000L
    }

    fun sopText(): String = """
        SOP مشکات — خلاصه عملیاتی
        1) ورود دو لایه (ماشین‌حساب + رمز برنامه)
        2) نام فقط مستعار عملیاتی — بدون شماره/ایمیل
        3) عضویت فقط با دعوت امضاشده و تأیید فرمانده
        4) Safety Number رو در رو قبل از خصوصی
        5) تمرین ماهانه: Duress + Panic (بدون داده واقعی)
        6) ابطال گواهی در CRL هنگام خروج نیرو
        7) پشتیبان کلید فقط با Shamir چندنفره
        8) رله اینترنت در پروفایل سازمانی خاموش
    """.trimIndent()
}
