package com.ghostmago.app.orgtrust

import android.content.Context
import android.util.Base64
import com.ghostmago.app.data.AppState
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

/**
 * Organizational identity & trust for مشکات:
 * 1) Org CA + signed member certificates
 * 2) One-time / limited signed invite tokens (QR)
 * 3) Offline CRL synced by commander
 * 4) Operational alias only (no real-name/phone in mesh identity)
 */
object OrgTrust {
    private const val PREF = "mishkat_org_trust_v1"
    private const val EC_CURVE = "secp256r1"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ─── helpers ───────────────────────────────────────────
    private fun b64(data: ByteArray) = Base64.encodeToString(data, Base64.NO_WRAP)
    private fun b64d(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun genEcKeyPair(): Pair<ECPrivateKey, ECPublicKey> {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec(EC_CURVE), SecureRandom())
        val kp = kpg.generateKeyPair()
        return kp.private as ECPrivateKey to kp.public as ECPublicKey
    }

    private fun sign(privateKey: ECPrivateKey, message: ByteArray): ByteArray {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey)
        sig.update(message)
        return sig.sign()
    }

    private fun verify(publicKey: ECPublicKey, message: ByteArray, signature: ByteArray): Boolean {
        return try {
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(message)
            sig.verify(signature)
        } catch (_: Exception) {
            false
        }
    }

    private fun loadPrivate(b64Key: String): ECPrivateKey {
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePrivate(PKCS8EncodedKeySpec(b64d(b64Key))) as ECPrivateKey
    }

    private fun loadPublic(b64Key: String): ECPublicKey {
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePublic(X509EncodedKeySpec(b64d(b64Key))) as ECPublicKey
    }

    // ─── 1) CA + certificates ──────────────────────────────
    data class OrgCa(
        val caId: String,
        val publicB64: String,
        val hasPrivate: Boolean
    )

    data class MemberCert(
        val serial: String,
        val alias: String,
        val role: String,
        val pubB64: String,
        val issuedAt: Long,
        val expiresAt: Long,
        val caId: String,
        val signatureB64: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("v", 1)
            .put("serial", serial)
            .put("alias", alias)
            .put("role", role)
            .put("pub", pubB64)
            .put("iat", issuedAt)
            .put("exp", expiresAt)
            .put("caId", caId)
            .put("sig", signatureB64)

        fun payloadForSign(): ByteArray =
            JSONObject()
                .put("v", 1)
                .put("serial", serial)
                .put("alias", alias)
                .put("role", role)
                .put("pub", pubB64)
                .put("iat", issuedAt)
                .put("exp", expiresAt)
                .put("caId", caId)
                .toString()
                .toByteArray(Charsets.UTF_8)

        companion object {
            fun fromJson(o: JSONObject): MemberCert = MemberCert(
                serial = o.getString("serial"),
                alias = o.getString("alias"),
                role = o.optString("role", "soldier"),
                pubB64 = o.getString("pub"),
                issuedAt = o.getLong("iat"),
                expiresAt = o.getLong("exp"),
                caId = o.getString("caId"),
                signatureB64 = o.getString("sig")
            )
        }
    }

    fun hasLocalCa(context: Context): Boolean =
        prefs(context).contains("ca_priv") && prefs(context).contains("ca_pub")

    fun getCaPublic(context: Context): OrgCa? {
        val p = prefs(context)
        val id = p.getString("ca_id", null) ?: return null
        val pub = p.getString("ca_pub", null) ?: return null
        return OrgCa(id, pub, p.contains("ca_priv"))
    }

    /** Commander initializes organizational CA on this device. */
    fun initCa(context: Context): OrgCa {
        val (priv, pub) = genEcKeyPair()
        val caId = UUID.randomUUID().toString().take(12)
        prefs(context).edit()
            .putString("ca_id", caId)
            .putString("ca_priv", b64(priv.encoded))
            .putString("ca_pub", b64(pub.encoded))
            .apply()
        // Exportable public only blob for members
        prefs(context).edit().putString("ca_pub_export", b64(pub.encoded)).apply()
        return OrgCa(caId, b64(pub.encoded), true)
    }

    fun importCaPublic(context: Context, caId: String, publicB64: String) {
        prefs(context).edit()
            .putString("ca_id", caId)
            .putString("ca_pub", publicB64)
            .remove("ca_priv") // members never hold CA private
            .apply()
    }

    fun exportCaPublicBundle(context: Context): String {
        val ca = getCaPublic(context) ?: throw IllegalStateException("CA وجود ندارد")
        return JSONObject()
            .put("v", 1)
            .put("caId", ca.caId)
            .put("pub", ca.publicB64)
            .toString()
    }

    fun importCaPublicBundle(context: Context, json: String) {
        val o = JSONObject(json)
        importCaPublic(context, o.getString("caId"), o.getString("pub"))
    }

    /**
     * Issue member certificate (commander only — needs CA private key).
     * Alias must be operational (IdentityPolicy).
     */
    fun issueCertificate(
        context: Context,
        alias: String,
        role: String,
        memberPubB64: String? = null,
        validDays: Int = 30
    ): MemberCert {
        val p = prefs(context)
        val caPrivB64 = p.getString("ca_priv", null)
            ?: throw IllegalStateException("فقط دارنده CA می‌تواند گواهی صادر کند")
        val caId = p.getString("ca_id", null) ?: throw IllegalStateException("CA ناقص است")
        val cleanAlias = IdentityPolicy.sanitizeAlias(alias)
        val (memberPriv, memberPub) = if (memberPubB64 == null) {
            genEcKeyPair()
        } else {
            null to loadPublic(memberPubB64)
        }
        val pubB64 = memberPubB64 ?: b64(memberPub.encoded)
        val now = System.currentTimeMillis() / 1000
        val serial = UUID.randomUUID().toString().replace("-", "").take(16)
        val cert = MemberCert(
            serial = serial,
            alias = cleanAlias,
            role = role,
            pubB64 = pubB64,
            issuedAt = now,
            expiresAt = now + validDays * 86400L,
            caId = caId,
            signatureB64 = ""
        )
        val sig = sign(loadPrivate(caPrivB64), cert.payloadForSign())
        val signed = cert.copy(signatureB64 = b64(sig))
        // Store issued cert + optional member private for local device
        p.edit().putString("cert_$serial", signed.toJson().toString()).apply()
        if (memberPriv != null) {
            p.edit()
                .putString("my_cert", signed.toJson().toString())
                .putString("my_member_priv", b64(memberPriv.encoded))
                .apply()
        }
        return signed
    }

    fun setMyCertificate(context: Context, certJson: String) {
        prefs(context).edit().putString("my_cert", certJson).apply()
    }

    fun getMyCertificate(context: Context): MemberCert? {
        val s = prefs(context).getString("my_cert", null) ?: return null
        return try {
            MemberCert.fromJson(JSONObject(s))
        } catch (_: Exception) {
            null
        }
    }

    fun verifyCertificate(context: Context, cert: MemberCert): String? {
        val caPub = prefs(context).getString("ca_pub", null)
            ?: return "کلید عمومی CA موجود نیست"
        if (cert.caId != prefs(context).getString("ca_id", cert.caId)) {
            // soft: allow if same pub imported under id
        }
        val now = System.currentTimeMillis() / 1000
        if (now > cert.expiresAt) return "گواهی منقضی شده"
        if (CrlStore.isRevoked(context, cert.serial)) return "گواهی ابطال شده (CRL)"
        val ok = verify(loadPublic(caPub), cert.payloadForSign(), b64d(cert.signatureB64))
        return if (ok) null else "امضای گواهی نامعتبر است"
    }

    // ─── 2) Signed invite tokens ───────────────────────────
    data class InviteToken(
        val tokenId: String,
        val groupId: String,
        val role: String,
        val expiresAt: Long,
        val maxUses: Int,
        val caId: String,
        val signatureB64: String
    ) {
        fun payloadForSign(): ByteArray =
            JSONObject()
                .put("v", 1)
                .put("tid", tokenId)
                .put("gid", groupId)
                .put("role", role)
                .put("exp", expiresAt)
                .put("uses", maxUses)
                .put("caId", caId)
                .toString()
                .toByteArray(Charsets.UTF_8)

        fun toQrPayload(): String =
            "MISHKAT-INV1:" + Base64.encodeToString(
                JSONObject()
                    .put("v", 1)
                    .put("tid", tokenId)
                    .put("gid", groupId)
                    .put("role", role)
                    .put("exp", expiresAt)
                    .put("uses", maxUses)
                    .put("caId", caId)
                    .put("sig", signatureB64)
                    .toString()
                    .toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )

        companion object {
            fun parseQr(raw: String): InviteToken? {
                return try {
                    var s = raw.trim()
                    val idx = s.indexOf("MISHKAT-INV1:")
                    if (idx < 0) return null
                    s = s.substring(idx + "MISHKAT-INV1:".length)
                    val o = JSONObject(String(Base64.decode(s, Base64.NO_WRAP), Charsets.UTF_8))
                    InviteToken(
                        tokenId = o.getString("tid"),
                        groupId = o.getString("gid"),
                        role = o.optString("role", "soldier"),
                        expiresAt = o.getLong("exp"),
                        maxUses = o.optInt("uses", 1),
                        caId = o.getString("caId"),
                        signatureB64 = o.getString("sig")
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    fun issueInvite(
        context: Context,
        groupId: String,
        role: String = "soldier",
        ttlHours: Int = 24,
        maxUses: Int = 1
    ): InviteToken {
        val p = prefs(context)
        val caPriv = p.getString("ca_priv", null)
            ?: throw IllegalStateException("صدور دعوت فقط با CA")
        val caId = p.getString("ca_id", null) ?: throw IllegalStateException("CA ناقص")
        val now = System.currentTimeMillis() / 1000
        val token = InviteToken(
            tokenId = UUID.randomUUID().toString().replace("-", "").take(12),
            groupId = groupId.ifBlank { "default" },
            role = role,
            expiresAt = now + ttlHours * 3600L,
            maxUses = maxUses.coerceIn(1, 50),
            caId = caId,
            signatureB64 = ""
        )
        val sig = sign(loadPrivate(caPriv), token.payloadForSign())
        val signed = token.copy(signatureB64 = b64(sig))
        // track uses locally
        p.edit()
            .putInt("inv_uses_${signed.tokenId}", 0)
            .putInt("inv_max_${signed.tokenId}", signed.maxUses)
            .putLong("inv_exp_${signed.tokenId}", signed.expiresAt)
            .apply()
        return signed
    }

    fun verifyInvite(context: Context, token: InviteToken): String? {
        val caPub = prefs(context).getString("ca_pub", null) ?: return "CA موجود نیست"
        val now = System.currentTimeMillis() / 1000
        if (now > token.expiresAt) return "توکن دعوت منقضی شده"
        if (!verify(loadPublic(caPub), token.payloadForSign(), b64d(token.signatureB64))) {
            return "امضای دعوت نامعتبر"
        }
        val used = prefs(context).getInt("inv_uses_${token.tokenId}", 0)
        val max = prefs(context).getInt("inv_max_${token.tokenId}", token.maxUses)
        // If commander never saw token id, accept signature+expiry (member scanned)
        if (prefs(context).contains("inv_max_${token.tokenId}") && used >= max) {
            return "سقف استفاده از توکن تمام شده"
        }
        return null
    }

    fun consumeInvite(context: Context, tokenId: String) {
        val p = prefs(context)
        val used = p.getInt("inv_uses_$tokenId", 0)
        p.edit().putInt("inv_uses_$tokenId", used + 1).apply()
    }

    // ─── pending invite for join ───────────────────────────
    fun savePendingInvite(context: Context, tokenJson: String) {
        prefs(context).edit().putString("pending_invite", tokenJson).apply()
    }

    fun getPendingInvite(context: Context): InviteToken? {
        val s = prefs(context).getString("pending_invite", null) ?: return null
        return try {
            val o = JSONObject(s)
            InviteToken(
                tokenId = o.getString("tid"),
                groupId = o.getString("gid"),
                role = o.optString("role", "soldier"),
                expiresAt = o.getLong("exp"),
                maxUses = o.optInt("uses", 1),
                caId = o.getString("caId"),
                signatureB64 = o.getString("sig")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clearPendingInvite(context: Context) {
        prefs(context).edit().remove("pending_invite").apply()
    }
}

/**
 * Offline Certificate Revocation List — commander maintains, can export/import JSON.
 */
object CrlStore {
    private const val PREF = "mishkat_crl_v1"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun revoke(context: Context, serial: String, reason: String = "revoked") {
        val p = prefs(context)
        val arr = try {
            JSONArray(p.getString("revoked", "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
        // avoid dup
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("serial") == serial) return
        }
        arr.put(
            JSONObject()
                .put("serial", serial)
                .put("ts", System.currentTimeMillis() / 1000)
                .put("reason", reason)
        )
        p.edit()
            .putString("revoked", arr.toString())
            .putLong("updated", System.currentTimeMillis() / 1000)
            .apply()
    }

    fun isRevoked(context: Context, serial: String): Boolean {
        val arr = try {
            JSONArray(prefs(context).getString("revoked", "[]"))
        } catch (_: Exception) {
            return false
        }
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("serial") == serial) return true
        }
        return false
    }

    fun exportJson(context: Context): String {
        val p = prefs(context)
        return JSONObject()
            .put("v", 1)
            .put("app", "مشکات")
            .put("updated", p.getLong("updated", 0))
            .put("revoked", JSONArray(p.getString("revoked", "[]")))
            .toString()
    }

    fun importJson(context: Context, json: String) {
        val o = JSONObject(json)
        val incoming = o.getJSONArray("revoked")
        val p = prefs(context)
        val existing = try {
            JSONArray(p.getString("revoked", "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
        val seen = mutableSetOf<String>()
        val merged = JSONArray()
        fun addAll(src: JSONArray) {
            for (i in 0 until src.length()) {
                val e = src.getJSONObject(i)
                val s = e.optString("serial")
                if (s.isNotBlank() && seen.add(s)) merged.put(e)
            }
        }
        addAll(existing)
        addAll(incoming)
        p.edit()
            .putString("revoked", merged.toString())
            .putLong("updated", System.currentTimeMillis() / 1000)
            .apply()
    }

    fun listSerials(context: Context): List<String> {
        val arr = try {
            JSONArray(prefs(context).getString("revoked", "[]"))
        } catch (_: Exception) {
            return emptyList()
        }
        return (0 until arr.length()).map { arr.getJSONObject(it).optString("serial") }
    }
}

/**
 * Operational identity policy: alias only, strip phone-like data from logs/identity.
 */
object IdentityPolicy {
    private val phoneLike = Regex("""(\+?\d[\d\s\-]{7,}\d)""")
    private val emailLike = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

    fun sanitizeAlias(raw: String): String {
        var s = raw.trim()
            .replace(phoneLike, "…")
            .replace(emailLike, "…")
            .replace(Regex("""\s+"""), "-")
        if (s.length < 2) s = "OP-" + (1000 + SecureRandom().nextInt(9000))
        return s.take(24)
    }

    fun sanitizeLog(text: String): String =
        text.replace(phoneLike, "[شماره حذف‌شد]")
            .replace(emailLike, "[ایمیل حذف‌شد]")

    fun enforceOperationalNickname(context: Context) {
        val alias = AppState.nickname
        val clean = sanitizeAlias(alias)
        if (clean != alias) {
            AppState.nickname = clean
            AppState.save(context)
        }
    }

    fun requireAliasOrDefault(context: Context): String {
        enforceOperationalNickname(context)
        return AppState.nickname
    }
}
