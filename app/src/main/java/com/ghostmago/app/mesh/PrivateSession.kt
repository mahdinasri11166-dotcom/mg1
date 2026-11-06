package com.ghostmago.app.mesh

import com.ghostmago.app.crypto.CryptoEngine
import com.ghostmago.app.crypto.DoubleRatchet
import com.ghostmago.app.crypto.E2E
import com.ghostmago.app.crypto.X3DH
import com.ghostmago.app.crypto.CryptoPolicy
import android.util.Base64
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Private sessions: prefer X3DH when peer bundle available, else symmetric fallback.
 */
object PrivateSessionStore {
    data class Session(
        val peerHash: String,
        val peerNick: String,
        val ratchet: DoubleRatchet,
        val fingerprint: String,
        val iAmAlice: Boolean,
        val x3dh: Boolean,
        val createdAt: Long = System.currentTimeMillis()
    )

    private val sessions = ConcurrentHashMap<String, Session>()
    private var localKeys: X3DH.LocalKeys? = null

    fun local(): X3DH.LocalKeys {
        if (localKeys == null) localKeys = X3DH.generateLocal()
        return localKeys!!
    }

    fun myBundleJson(): String {
        val b = X3DH.bundle(local())
        return JSONObject().apply {
            put("ik", Base64.encodeToString(b.identityPub, Base64.NO_WRAP))
            put("spk", Base64.encodeToString(b.signedPreKeyPub, Base64.NO_WRAP))
            put("id", b.preKeyId)
        }.toString()
    }

    fun parseBundle(json: String): X3DH.PreKeyBundle? = try {
        val o = JSONObject(json)
        X3DH.PreKeyBundle(
            Base64.decode(o.getString("ik"), Base64.NO_WRAP),
            Base64.decode(o.getString("spk"), Base64.NO_WRAP),
            o.optInt("id", 0)
        )
    } catch (_: Exception) { null }

    fun fingerprintOf(data: ByteArray): String {
        val h = CryptoEngine.sha256(data)
        return h.take(8).joinToString(" ") { "%02X".format(it) }
    }

    /** X3DH Alice: returns (session, ephPub to send to Bob) */
    fun startX3DHAlice(
        myHash: String,
        peerHash: String,
        peerNick: String,
        bobBundle: X3DH.PreKeyBundle
    ): Pair<Session, ByteArray> {
        val (sk, ephPub) = X3DH.initiate(local(), bobBundle)
        val iAmAlice = true
        val ratchet = DoubleRatchet.initAlice(sk.copyOf())
        val sess = Session(peerHash, peerNick, ratchet, fingerprintOf(sk), iAmAlice, true)
        sessions[peerHash] = sess
        sk.fill(0)
        return sess to ephPub
    }

    fun startX3DHBob(
        peerHash: String,
        peerNick: String,
        aliceIk: ByteArray,
        aliceEph: ByteArray
    ): Session {
        val sk = X3DH.respond(local(), aliceIk, aliceEph)
        val ratchet = DoubleRatchet.initBob(sk.copyOf())
        val sess = Session(peerHash, peerNick, ratchet, fingerprintOf(sk), false, true)
        sessions[peerHash] = sess
        sk.fill(0)
        return sess
    }

    /** Fallback symmetric — blocked when organizational X3DH is required. */
    fun startSymmetric(
        myHash: String,
        peerHash: String,
        peerNick: String,
        groupPassword: String
    ): Session {
        require(CryptoPolicy.allowSymmetricPrivateFallback()) {
            "چت خصوصی فقط با X3DH+Double Ratchet مجاز است — باندل طرف را بگیرید"
        }
        sessions[peerHash]?.let { return it }
        require(groupPassword.length >= CryptoPolicy.MIN_GROUP_PASSWORD) { "رمز گروه حداقل ${CryptoPolicy.MIN_GROUP_PASSWORD} کاراکتر" }
        require(myHash != peerHash) { "نمیتوان با خود جلسه ساخت" }
        val a = minOf(myHash, peerHash)
        val b = maxOf(myHash, peerHash)
        val ikm = CryptoEngine.sha256("$groupPassword|$a|$b".toByteArray(Charsets.UTF_8))
        val iAmAlice = myHash <= peerHash
        val ratchet = if (iAmAlice) DoubleRatchet.initAlice(ikm.copyOf()) else DoubleRatchet.initBob(ikm.copyOf())
        val sess = Session(peerHash, peerNick, ratchet, fingerprintOf(ikm), iAmAlice, false)
        sessions[peerHash] = sess
        ikm.fill(0)
        return sess
    }

    fun get(peerHash: String) = sessions[peerHash]

    @Synchronized
    fun encrypt(peerHash: String, plaintext: String): ByteArray? {
        val s = sessions[peerHash] ?: return null
        return try {
            s.ratchet.encrypt(plaintext.toByteArray(Charsets.UTF_8)).toBytes()
        } catch (_: Exception) { null }
    }

    @Synchronized
    fun decrypt(peerHash: String, data: ByteArray): String? {
        val s = sessions[peerHash] ?: return null
        if (data.size < 21) return null
        return try {
            String(s.ratchet.decrypt(DoubleRatchet.EncryptedMessage.fromBytes(data)), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    fun remove(peerHash: String) { sessions.remove(peerHash) }
    fun clear() { sessions.clear() }
}
