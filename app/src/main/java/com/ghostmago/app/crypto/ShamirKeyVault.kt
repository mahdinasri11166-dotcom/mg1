package com.ghostmago.app.crypto

import android.content.Context
import android.util.Base64
import com.ghostmago.app.data.AppState
import com.ghostmago.app.data.VerifiedPeers
import org.json.JSONObject

/**
 * Multi-party Shamir backup of operational secrets (3 of 5 by default).
 * Each share is SHARE|i|hex|chk — no single share alone recovers the vault.
 */
object ShamirKeyVault {
    private const val MAGIC = "MKV1|"

    /**
     * Build plaintext vault JSON then encrypt with [password], then split ciphertext with Shamir.
     * Returns list of share strings for distribution to commanders.
     */
    fun exportShares(
        context: Context,
        password: String,
        n: Int = CryptoPolicy.SHAMIR_N,
        k: Int = CryptoPolicy.SHAMIR_K
    ): List<String> {
        require(password.length >= CryptoPolicy.MIN_BACKUP_PASSWORD) {
            "رمز پشتیبان حداقل ${CryptoPolicy.MIN_BACKUP_PASSWORD} کاراکتر"
        }
        val payload = JSONObject().apply {
            put("v", 2)
            put("app", "مشکات")
            put("ts", System.currentTimeMillis())
            put("nickname", AppState.nickname)
            put("meshRole", AppState.meshRole.value)
            put("groupId", AppState.groupId)
            put("lastGroupId", AppState.lastGroupId)
            put("lastGroupPassword", AppState.lastGroupPassword)
            put("lastMeshNickname", AppState.lastMeshNickname)
            put("roomsJson", AppState.roomsJson)
            put("appPasswordHash", AppState.appPasswordHash)
            put("duressPasswordHash", AppState.duressPasswordHash)
            put("verifiedPeers", VerifiedPeers.exportJson(context))
            put("transportMode", AppState.transportMode)
            put("meshPort", AppState.meshPort)
        }
        val enc = CryptoEngine.encrypt(
            payload.toString().toByteArray(Charsets.UTF_8),
            password.toCharArray()
        )
        val shares = Shamir.split(enc, n = n, k = k)
        return shares.map { (idx, bytes) ->
            Shamir.toShareLine(idx, bytes)
        }
    }

    fun recoverFromShares(shares: List<String>, password: String): ByteArray {
        require(password.length >= CryptoPolicy.MIN_BACKUP_PASSWORD)
        val parsed = shares.mapNotNull { Shamir.parseShareLine(it) }
        require(parsed.size >= CryptoPolicy.SHAMIR_K) { "حداقل ${CryptoPolicy.SHAMIR_K} سهم لازم است" }
        return Shamir.recover(parsed)
    }

    fun importFromShares(context: Context, shares: List<String>, password: String): String {
        val cipher = recoverFromShares(shares, password)
        val plain = CryptoEngine.decrypt(cipher, password.toCharArray())
        val o = JSONObject(String(plain, Charsets.UTF_8))
        AppState.nickname = o.optString("nickname", AppState.nickname)
        AppState.groupId = o.optString("groupId", AppState.groupId)
        AppState.lastGroupId = o.optString("lastGroupId", AppState.lastGroupId)
        AppState.lastGroupPassword = o.optString("lastGroupPassword", AppState.lastGroupPassword)
        AppState.lastMeshNickname = o.optString("lastMeshNickname", AppState.lastMeshNickname)
        AppState.roomsJson = o.optString("roomsJson", AppState.roomsJson)
        if (o.has("appPasswordHash") && o.getString("appPasswordHash").isNotBlank()) {
            AppState.appPasswordHash = o.getString("appPasswordHash")
        }
        if (o.has("duressPasswordHash")) {
            AppState.duressPasswordHash = o.optString("duressPasswordHash", "")
        }
        if (o.has("verifiedPeers")) VerifiedPeers.importJson(context, o.getString("verifiedPeers"))
        if (o.has("transportMode")) AppState.transportMode = o.optString("transportMode", "auto")
        if (o.has("meshPort")) AppState.meshPort = o.optInt("meshPort", 5599)
        AppState.save(context)
        return "بازیابی چندنفره موفق"
    }
}
