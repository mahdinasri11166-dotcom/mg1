package com.ghostmago.app.crypto

import android.content.Context
import android.util.Base64
import com.ghostmago.app.data.AppState
import com.ghostmago.app.data.RoomStore
import com.ghostmago.app.data.VerifiedPeers
import org.json.JSONObject

/**
 * Password-encrypted backup of operational keys/settings (not message history).
 * Format: MKB1|base64(AES-GCM(Argon2id(password)))
 */
object KeyBackup {
    private const val MAGIC = "MKB1|"

    fun export(context: Context, password: String): String {
        require(password.length >= 6) { "رمز پشتیبان حداقل ۶ کاراکتر" }
        val payload = JSONObject().apply {
            put("v", 1)
            put("app", "مشکات")
            put("ts", System.currentTimeMillis())
            put("nickname", AppState.nickname)
            put("meshRole", AppState.meshRole.value)
            put("groupId", AppState.groupId)
            put("lastGroupId", AppState.lastGroupId)
            put("lastGroupPassword", AppState.lastGroupPassword)
            put("lastMeshNickname", AppState.lastMeshNickname)
            put("roomsJson", AppState.roomsJson)
            put("vaultPin", AppState.vaultPin)
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
        return MAGIC + Base64.encodeToString(enc, Base64.NO_WRAP)
    }

    fun import(context: Context, password: String, blob: String): String {
        require(password.length >= 6) { "رمز پشتیبان حداقل ۶ کاراکتر" }
        val raw = blob.trim()
        require(raw.startsWith(MAGIC)) { "فایل پشتیبان نامعتبر است" }
        val b64 = raw.removePrefix(MAGIC)
        val data = Base64.decode(b64, Base64.NO_WRAP)
        val plain = CryptoEngine.decrypt(data, password.toCharArray())
        val o = JSONObject(String(plain, Charsets.UTF_8))
        AppState.nickname = o.optString("nickname", AppState.nickname)
        AppState.groupId = o.optString("groupId", AppState.groupId)
        AppState.lastGroupId = o.optString("lastGroupId", AppState.lastGroupId)
        AppState.lastGroupPassword = o.optString("lastGroupPassword", AppState.lastGroupPassword)
        AppState.lastMeshNickname = o.optString("lastMeshNickname", AppState.lastMeshNickname)
        AppState.roomsJson = o.optString("roomsJson", AppState.roomsJson)
        if (o.has("vaultPin")) AppState.vaultPin = o.getString("vaultPin")
        if (o.has("appPasswordHash") && o.getString("appPasswordHash").isNotBlank()) {
            AppState.appPasswordHash = o.getString("appPasswordHash")
        }
        if (o.has("duressPasswordHash")) {
            AppState.duressPasswordHash = o.optString("duressPasswordHash", "")
        }
        if (o.has("transportMode")) AppState.transportMode = o.optString("transportMode", "auto")
        if (o.has("meshPort")) AppState.meshPort = o.optInt("meshPort", 5599)
        if (o.has("verifiedPeers")) {
            VerifiedPeers.importJson(context, o.getString("verifiedPeers"))
        }
        AppState.save(context)
        // refresh room list side effect
        try {
            RoomStore.list()
        } catch (_: Exception) {
        }
        return "بازیابی شد — ${o.optString("app", "مشکات")}"
    }
}
