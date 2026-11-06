package com.ghostmago.app.mesh

import com.ghostmago.app.crypto.CryptoEngine
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signed control-plane messages (join / approve / reject / wipe / policy).
 * Separates trust of management traffic from data plane.
 */
object ControlPlane {
    private val CONTROL_TYPES = setOf(
        MeshPacket.TYPE_JOIN_REQ,
        MeshPacket.TYPE_JOIN_ACCEPT,
        MeshPacket.TYPE_JOIN_REJECT,
        MeshPacket.TYPE_REMOTE_WIPE,
        MeshPacket.TYPE_DEVICE_POLICY,
        MeshPacket.TYPE_ACK
    )

    fun isControl(type: Int): Boolean = type in CONTROL_TYPES

    /** Control UDP = data port + 1 */
    fun controlPort(dataPort: Int): Int = dataPort + 1

    private fun controlKey(groupId: String, groupPassword: String): ByteArray =
        CryptoEngine.sha256("ctrl|$groupId|$groupPassword".toByteArray(Charsets.UTF_8))

    fun signPayload(groupId: String, groupPassword: String, type: Int, json: JSONObject): JSONObject {
        val ts = System.currentTimeMillis() / 1000
        json.put("ctrl", 1)
        json.put("cts", ts)
        val body = "${type}|${json.optString("nick")}|${json.optLong("cts")}|${json.optString("role")}|${json.optString("cmd")}"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(controlKey(groupId, groupPassword), "HmacSHA256"))
        val sig = mac.doFinal(body.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
        json.put("csig", sig)
        return json
    }

    fun verifyPayload(groupId: String, groupPassword: String, type: Int, json: JSONObject, maxSkewSec: Long = 90): String? {
        if (!json.has("csig")) return null // legacy peers without signature still accepted
        val ts = json.optLong("cts", 0)
        val now = System.currentTimeMillis() / 1000
        if (ts > 0 && kotlin.math.abs(now - ts) > maxSkewSec) return "پنجره زمانی کنترل نامعتبر"
        val body = "${type}|${json.optString("nick")}|${json.optLong("cts")}|${json.optString("role")}|${json.optString("cmd")}"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(controlKey(groupId, groupPassword), "HmacSHA256"))
        val expect = mac.doFinal(body.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
        val got = json.optString("csig")
        if (!got.equals(expect, ignoreCase = true)) return "امضای کنترل نامعتبر"
        return null
    }
}

/**
 * Cover traffic that mimics recent real sizes and inter-arrival.
 */
object CoverTrafficProfile {
    private val recentSizes = ArrayDeque<Int>()
    private const val MAX = 24

    fun noteRealPacket(size: Int) {
        synchronized(recentSizes) {
            recentSizes.addLast(size.coerceIn(32, 1200))
            while (recentSizes.size > MAX) recentSizes.removeFirst()
        }
    }

    fun nextCoverSize(): Int {
        synchronized(recentSizes) {
            if (recentSizes.isEmpty()) return 48 + kotlin.random.Random.nextInt(80)
            val sample = recentSizes.random()
            val jitter = kotlin.random.Random.nextInt(-16, 24)
            return (sample + jitter).coerceIn(32, 900)
        }
    }

    fun nextDelayMs(baseSec: Int): Long {
        val base = baseSec.coerceIn(3, 120) * 1000L
        // Log-normal-ish jitter
        val jitter = kotlin.random.Random.nextLong(-base / 3, base / 2)
        return (base + jitter).coerceAtLeast(1500L)
    }
}
