package com.ghostmago.app.mesh

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.ghostmago.app.crypto.CryptoEngine
import com.ghostmago.app.data.AppState
import com.ghostmago.app.util.DeviceGuard
import com.ghostmago.app.util.UiThrottle
import com.ghostmago.app.orgtrust.OrgTrust
import com.ghostmago.app.orgtrust.IdentityPolicy
import com.ghostmago.app.data.MeshRole
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class MeshNetwork(
    private val context: Context,
    private val myId: String,
    private val nickname: String,
    private val groupId: String,
    private val groupPassword: String,
    private val role: MeshRole,
    /** true = created/host network (no wait); false = joined via invite and needs approval */
    private val hostMode: Boolean = true,
    private val onText: (origin: String, nick: String, text: String, hops: Int, ephemeralSec: Int) -> Unit,
    private val onVoice: (origin: String, nick: String, audio: ByteArray, hops: Int) -> Unit,
    private val onFile: (origin: String, nick: String, filename: String, data: ByteArray, hops: Int) -> Unit,
    private val onImage: (origin: String, nick: String, jpeg: ByteArray, caption: String, hops: Int) -> Unit = { _,_,_,_,_ -> },
    private val onDelivered: (msgId: String) -> Unit = {},
    private val onDeliveryFailed: (msgId: String) -> Unit = {},
    private val onJoinReq: (origin: String, nick: String, role: String) -> Unit = { _, _, _ -> },
    private val onPrivate: (origin: String, nick: String, text: String) -> Unit = { _, _, _ -> },
    private val onPrivHs: (origin: String, nick: String, staticPubB64: String, ephPubB64: String) -> Unit = { _, _, _, _ -> },
    private val onMemberUpdate: () -> Unit = {},
    private val onStatus: (String) -> Unit = {},
    private val onJoinAccepted: (role: String) -> Unit = {},
    private val onCallSignal: (origin: String, nick: String, action: String, extra: String) -> Unit = { _, _, _, _ -> }
) {
    companion object {
        const val DEFAULT_PORT = 5599
        fun currentPort(): Int = try {
            AppState.meshPort.coerceIn(1024, 65535)
        } catch (_: Exception) { DEFAULT_PORT }
        const val MAX_PACKET = 60000
        const val BEACON_INTERVAL = 15000L
        const val ONLINE_TIMEOUT = 20000L
        /* DEFAULT_TTL */ const val DEFAULT_TTL = 8
        private const val TAG = "MeshNetwork"
    }

    private val myHash = MeshPacket.hashId(myId)
    private var socket: DatagramSocket? = null
    private var controlSocket: DatagramSocket? = null
    private var internetRelay: MeshRelay? = null
    private var btBridge: BluetoothMeshBridge? = null
    private var usbRadio: UsbRadioBridge? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    @Volatile private var membershipAccepted: Boolean = hostMode
    private val sessionCrypto = MeshSessionCrypto(groupPassword, groupId)
    private var running = false
    private var scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Mesh coroutine error", e)
            try { emitStatus("خطای شبکه (بازیابی)") } catch (_: Exception) {}
        }
    )
    private val seen = ConcurrentHashMap<String, Long>()
    private val statusThrottle = UiThrottle(700L)
    private fun emitStatus(msg: String) {
        if (!statusThrottle.accept(msg)) return
        try { onStatus(msg) } catch (_: Exception) {}
    }
    private val members = ConcurrentHashMap<String, MemberInfo>()
    private val pending = ConcurrentHashMap<String, MemberInfo>()
    private val neighbors = ConcurrentHashMap<String, NeighborInfo>()
    /** Offline store-and-forward: targetHash -> list of packets to deliver later */
    private val storeForward = ConcurrentHashMap<String, MutableList<ByteArray>>()
    private val pendingAcks = ConcurrentHashMap<String, Long>()
    private val chunkBuffers = ConcurrentHashMap<String, Array<ByteArray?>>()
    private val chunkMeta = ConcurrentHashMap<String, Pair<String, String>>() // fid -> filename, nick
    private val versionWarned = ConcurrentHashMap.newKeySet<String>()
    private val delivered = ConcurrentHashMap.newKeySet<String>()
    private val recentRealSends = java.util.concurrent.atomic.AtomicInteger(0)
    private val PENDING_TIMEOUT_MS = 25_000L

    data class MemberInfo(val nick: String, val role: String, val joinedAt: Long, val lastSeen: Long = joinedAt)
    data class NeighborInfo(val nick: String, val lastSeen: Long, val ip: String, val hops: Int)

    fun start() {
        if (running) return
        if (!scope.isActive) {
            scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Mesh coroutine error", e)
            try { emitStatus("خطای شبکه (بازیابی)") } catch (_: Exception) {}
        }
    )
        }
        try {
            // Release previous socket / service to avoid EADDRINUSE
            try { MeshService.stop(context) } catch (_: Exception) {}
            try { socket?.close() } catch (_: Exception) {}
            socket = null
            try { Thread.sleep(120) } catch (_: Exception) {}
            val s = DatagramSocket(null)
            s.reuseAddress = true
            s.broadcast = true
            s.soTimeout = 1000
            val port = currentPort()
            try {
                s.bind(java.net.InetSocketAddress(java.net.InetAddress.getByName("0.0.0.0"), port))
            } catch (bindEx: java.net.BindException) {
                try { s.close() } catch (_: Exception) {}
                try { Thread.sleep(300) } catch (_: Exception) {}
                val s2 = DatagramSocket(null)
                s2.reuseAddress = true
                s2.broadcast = true
                s2.soTimeout = 1000
                s2.bind(java.net.InetSocketAddress(java.net.InetAddress.getByName("0.0.0.0"), port))
                socket = s2
                running = true
                members[myHash] = MemberInfo(nickname, role.value, System.currentTimeMillis())
                scope.launch { recvLoop() }
                if (!AppState.radioSilence) {
                    scope.launch { beaconLoop() }
                }
                scope.launch { cleanupLoop() }
                scope.launch { coverLoop() }
                scope.launch { ackRetryLoop() }
                try {
                    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    multicastLock = wifi.createMulticastLock("mishkat_mesh").also {
                        it.setReferenceCounted(true)
                        it.acquire()
                    }
                } catch (_: Exception) {}
                MeshService.start(context)
                if (!membershipAccepted) scope.launch { joinRetryLoop() }
                emitStatus("فعال — پورت $port (بازیابی bind)")
                Log.i(TAG, "Mesh started after rebind")
                return
            }
            socket = s
            running = true
            members[myHash] = MemberInfo(nickname, role.value, System.currentTimeMillis())

            try {
                if (AppState.separateControlPlane) {
                    val cs = DatagramSocket(null)
                    cs.reuseAddress = true
                    cs.broadcast = true
                    cs.soTimeout = 1000
                    cs.bind(java.net.InetSocketAddress(java.net.InetAddress.getByName("0.0.0.0"), ControlPlane.controlPort(port)))
                    controlSocket = cs
                    scope.launch { controlRecvLoop() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "control bind", e)
                emitStatus("کانال کنترل روی همان پورت داده")
            }
            scope.launch { recvLoop() }
            if (!AppState.radioSilence) {
                scope.launch { beaconLoop() }
            }
            scope.launch { cleanupLoop() }
            scope.launch { coverLoop() }
            scope.launch { ackRetryLoop() }
            try {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = wifi.createMulticastLock("mishkat_mesh").also {
                    it.setReferenceCounted(true)
                    it.acquire()
                }
            } catch (_: Exception) {}
            MeshService.start(context)
            if (!membershipAccepted) {
                scope.launch { joinRetryLoop() }
            }
            try {
                internetRelay = MeshRelay(
                    onPacket = { data -> handlePacket(data, "relay") },
                    onStatus = { emitStatus(it) }
                ).also { if (AppState.relayEnabled && !AppState.orgBlockInternetRelay) it.start() else if (AppState.relayEnabled && AppState.orgBlockInternetRelay) emitStatus("رله اینترنت در پروفایل سازمانی مسدود است") }
                btBridge = BluetoothMeshBridge(context, { data -> handlePacket(data, "bt") }, { emitStatus(it) })
                    .also { if (AppState.useBluetoothMesh) it.start() }
                usbRadio = UsbRadioBridge(context, { data -> handlePacket(data, "usb-radio") }, { emitStatus(it) })
                    .also { if (AppState.useUsbRadioMesh) it.start() }
            } catch (_: Exception) {}
            val health = try { MeshHealth.transportSummary(context) } catch (_: Exception) { "" }
            emitStatus(
                "فعال — پورت ${currentPort()}" +
                    (if (AppState.radioSilence) " (سکوت)" else "") +
                    (if (health.isNotBlank()) " | $health" else "")
            )
            try {
                if (AppState.groupEpochAutoRotate) sessionCrypto.maybeAutoRotate()
            } catch (_: Exception) {}
            // Device posture in background — never block mesh start
            scope.launch {
                try {
                    val posture = DeviceGuard.scanPosture(context)
                    if (posture.reaction == DeviceGuard.Reaction.DISCONNECT && AppState.orgDeviceGuard) {
                        emitStatus("ضددستکاری: قطع مش — ${posture.detail}")
                        stop()
                    } else if (posture.reaction == DeviceGuard.Reaction.READ_ONLY && AppState.orgDeviceGuard) {
                        AppState.forceReadOnly = true
                        emitStatus("ضددستکاری: فقط‌خواندنی — ${posture.detail}")
                    } else if (posture.reaction == DeviceGuard.Reaction.WARN && AppState.orgDeviceGuard) {
                        emitStatus("هشدار دستگاه: ${posture.detail}")
                    }
                } catch (_: Exception) {}
            }
            Log.i(TAG, "Mesh started epoch="+sessionCrypto.currentEpoch())
        } catch (e: Exception) {
            emitStatus(if (e is java.net.BindException || (e.message?:"").contains("EADDRINUSE")) "پورت مش در حال استفاده است — Stop بزنید یا پورت را در تنظیمات عوض کنید" else "خطا: ${e.message}")
            Log.e(TAG, "Start failed", e)
        }
    }

    /** Soft restart without clearing membership — recovery from transient radio loss. */
    fun softReconnect() {
        if (!running) {
            start()
            return
        }
        scope.launch {
            try {
                emitStatus("بازیابی کانال…")
                delay(200)
                if (!membershipAccepted) sendJoinRequest()
                if (!AppState.radioSilence) {
                    val payload = JSONObject().apply {
                        put("nick", nickname); put("role", role.value)
                        put("appVer", com.ghostmago.app.BuildConfig.VERSION_NAME)
                        put("proto", 5)
                        put("ts", System.currentTimeMillis() / 1000.0)
                    }
                    sendPacket(MeshPacket.TYPE_BEACON, "", encryptPayload(payload.toString()), ttl = 1)
                }
                emitStatus("بازیابی انجام شد — پورت ${currentPort()}")
            } catch (e: Exception) {
                emitStatus("بازیابی ناموفق: ${e.message}")
            }
        }
    }

    fun stop() {
        sessionCrypto.clear()
        running = false
        scope.cancel()
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        try { controlSocket?.close() } catch (_: Exception) {}
        controlSocket = null
        try { internetRelay?.stop() } catch (_: Exception) {}
        internetRelay = null
        try { btBridge?.stop() } catch (_: Exception) {}
        btBridge = null
        try { usbRadio?.stop() } catch (_: Exception) {}
        usbRadio = null
        try { multicastLock?.release() } catch (_: Exception) {}
        multicastLock = null
        MeshService.stop(context)
        emitStatus("غیرفعال")
    }

    fun isRunning() = running
    fun offlineQueueSize(): Int = storeForward.values.sumOf { it.size }
    fun transportLabel(): String = try { TransportProfile.labelFa() } catch (_: Exception) { "—" }
    fun getMembers() = members.toList()

    /** hash, nick, role, statusFa (آنلاین/آفلاین), lastSeen */
    fun getMemberStatuses(): List<MemberStatus> {
        val now = System.currentTimeMillis()
        return members.map { (hash, info) ->
            val neigh = neighbors[hash]
            val seen = maxOf(info.lastSeen, neigh?.lastSeen ?: 0L)
            val online = hash == myHash || (now - seen < ONLINE_TIMEOUT)
            MemberStatus(hash, info.nick, info.role, if (online) "آنلاین" else "آفلاین", seen, online)
        }.sortedByDescending { it.online }
    }

    data class MemberStatus(
        val hash: String,
        val nick: String,
        val role: String,
        val statusFa: String,
        val lastSeen: Long,
        val online: Boolean
    )
    fun getPending() = pending.toList()
    fun getOnlineNeighbors(): List<Pair<String, NeighborInfo>> {
        val now = System.currentTimeMillis()
        return neighbors.filter { now - it.value.lastSeen < ONLINE_TIMEOUT }.toList()
    }

    fun sendText(text: String, targetHash: String = "", ephemeralSec: Int = 0): String {
        val payload = JSONObject().apply {
            put("nick", nickname)
            put("role", role.value)
            put("text", text)
            put("ts", System.currentTimeMillis() / 1000.0)
            if (ephemeralSec > 0) put("ephemeral", ephemeralSec)
        }
        var bytes = encryptPayload(payload.toString())
        if (AppState.sealedSender) {
            bytes = SealedSender.seal(myHash, nickname, bytes, groupPassword)
            // mark sealed with empty nick outer — handle on recv
            val outer = JSONObject().apply {
                put("sealed", true)
                put("blob", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                put("ts", System.currentTimeMillis() / 1000.0)
            }
            return sendPacket(MeshPacket.TYPE_DATA, targetHash, encryptPayload(outer.toString()))
        }
        return sendPacket(MeshPacket.TYPE_DATA, targetHash, bytes)
    }

    fun sendBroadcast(text: String) {
        if (!role.canCommand()) return
        val payload = JSONObject().apply {
            put("nick", nickname); put("role", role.value)
            put("text", text); put("ts", System.currentTimeMillis() / 1000.0)
        }
        sendPacket(MeshPacket.TYPE_BROADCAST, "*", encryptPayload(payload.toString()))
    }

    fun sendImage(jpeg: ByteArray, caption: String = ""): String {
        // Large images go through chunked file path
        if (jpeg.size > 24_000) {
            return sendFileChunked("image_${System.currentTimeMillis()}.jpg", jpeg)
        }
        val b64 = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
        val payload = org.json.JSONObject().apply {
            put("nick", nickname); put("role", role.value)
            put("img", b64); put("cap", caption)
            put("ts", System.currentTimeMillis() / 1000.0)
        }
        return sendPacket(MeshPacket.TYPE_IMAGE, "", encryptPayload(payload.toString()))
    }

    fun sendVoice(audio: ByteArray): String {
        if (audio.isEmpty()) return ""
        return try {
            val b64 = android.util.Base64.encodeToString(audio, android.util.Base64.NO_WRAP)
            val payload = JSONObject().apply {
                put("nick", nickname); put("role", role.value)
                put("audio", b64); put("ts", System.currentTimeMillis() / 1000.0)
            }
            sendPacket(MeshPacket.TYPE_VOICE, "", encryptPayload(payload.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "sendVoice", e)
            ""
        }
    }

    /** Private voice note or live-call chunk to one peer. */
    fun sendPrivateVoice(peerHash: String, audio: ByteArray, isCall: Boolean = false): String {
        if (peerHash.isBlank() || audio.isEmpty()) return ""
        val b64 = android.util.Base64.encodeToString(audio, android.util.Base64.NO_WRAP)
        val payload = JSONObject().apply {
            put("nick", nickname); put("role", role.value)
            put("audio", b64)
            put("priv", true)
            put("call", isCall)
            put("ts", System.currentTimeMillis() / 1000.0)
        }
        return sendPacket(MeshPacket.TYPE_VOICE, peerHash, encryptPayload(payload.toString()), ttl = 8)
    }

    fun sendCallSignal(
        peerHash: String,
        action: String,
        sdpType: String = "",
        sdp: String = "",
        iceMid: String = "",
        iceIndex: Int = 0,
        iceCand: String = ""
    ): String {
        if (peerHash.isBlank()) return ""
        val payload = JSONObject().apply {
            put("nick", nickname); put("role", role.value)
            put("callSignal", action) // ring | accept | end | sdp | ice | busy
            if (sdpType.isNotBlank()) put("sdpType", sdpType)
            if (sdp.isNotBlank()) put("sdp", sdp)
            if (iceCand.isNotBlank()) {
                put("iceMid", iceMid)
                put("iceIndex", iceIndex)
                put("ice", iceCand)
            }
            put("ts", System.currentTimeMillis() / 1000.0)
        }
        return sendPacket(MeshPacket.TYPE_PRIVATE, peerHash, encryptPayload(payload.toString()), ttl = 8)
    }

    fun sendFile(filename: String, data: ByteArray): String {
        if (data.size > 24_000) return sendFileChunked(filename, data)
        val b64 = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
        val payload = JSONObject().apply {
            put("nick", nickname); put("role", role.value)
            put("filename", filename); put("data", b64)
            put("ts", System.currentTimeMillis() / 1000.0)
        }
        return sendPacket(MeshPacket.TYPE_FILE, "", encryptPayload(payload.toString()))
    }

    /** Multi-megabyte transfer via ordered chunks (~12KB payload each). */
    fun sendFileChunked(filename: String, data: ByteArray, maxBytes: Int = 8_000_000): String {
        if (data.size > maxBytes) {
            emitStatus("فایل خیلی بزرگ است (حداکثر ${maxBytes / 1_000_000}MB)")
            return ""
        }
        val chunkSize = 12_000
        val total = (data.size + chunkSize - 1) / chunkSize
        val fileId = MeshPacket.makeMsgId(myHash).take(12)
        emitStatus("ارسال تکهای: $filename — $total تکه")
        var lastId = ""
        for (i in 0 until total) {
            val from = i * chunkSize
            val to = minOf(from + chunkSize, data.size)
            val part = data.copyOfRange(from, to)
            val b64 = android.util.Base64.encodeToString(part, android.util.Base64.NO_WRAP)
            val payload = JSONObject().apply {
                put("nick", nickname); put("role", role.value)
                put("appVer", com.ghostmago.app.BuildConfig.VERSION_NAME)
                put("proto", 5)
                put("fid", fileId); put("i", i); put("n", total)
                put("filename", filename); put("data", b64)
                put("ts", System.currentTimeMillis() / 1000.0)
            }
            lastId = sendPacket(MeshPacket.TYPE_CHUNK, "", encryptPayload(payload.toString()), ttl = 8)
        }
        return lastId
    }

    fun sendJoinRequest() {
        val safeNick = try {
            IdentityPolicy.sanitizeAlias(nickname)
        } catch (_: Exception) {
            nickname
        }
        val payload = JSONObject().apply {
            put("nick", safeNick)
            put("role", role.value)
            put("appVer", com.ghostmago.app.BuildConfig.VERSION_NAME)
            put("proto", 7)
            put("ts", System.currentTimeMillis() / 1000.0)
            try {
                OrgTrust.getMyCertificate(context)?.let { c ->
                    put("cert", c.toJson())
                }
            } catch (_: Exception) {}
            try {
                OrgTrust.getPendingInvite(context)?.let { inv ->
                    put("inviteTid", inv.tokenId)
                    put("invite", JSONObject()
                        .put("tid", inv.tokenId)
                        .put("gid", inv.groupId)
                        .put("role", inv.role)
                        .put("exp", inv.expiresAt)
                        .put("uses", inv.maxUses)
                        .put("caId", inv.caId)
                        .put("sig", inv.signatureB64)
                    )
                }
            } catch (_: Exception) {}
        }
        sendPacket(MeshPacket.TYPE_JOIN_REQ, "", encryptControl(MeshPacket.TYPE_JOIN_REQ, payload))
    }

    fun approveMember(originHash: String, newRole: String = "soldier", displayNick: String = "") {
        if (!role.canApprove() && !hostMode) return
        if (originHash.isBlank()) return
        val info = pending.remove(originHash)
        val nick = info?.nick?.ifBlank { null } ?: displayNick.ifBlank { "عضو" }
        members[originHash] = MemberInfo(nick, newRole, System.currentTimeMillis(), System.currentTimeMillis())
        // ensure not stuck in pending
        pending.remove(originHash)
        val payload = JSONObject().apply {
            put("role", newRole)
            put("nick", nickname)
            put("ts", System.currentTimeMillis() / 1000.0)
        }
        sendPacket(MeshPacket.TYPE_JOIN_ACCEPT, originHash, encryptControl(MeshPacket.TYPE_JOIN_ACCEPT, payload), ttl = 8)
        // also broadcast so multi-hop peers learn membership
        sendPacket(MeshPacket.TYPE_BEACON, "", encryptPayload(JSONObject().apply {
            put("nick", nickname); put("role", role.value)
            put("appVer", com.ghostmago.app.BuildConfig.VERSION_NAME)
            put("proto", 5)
            put("ts", System.currentTimeMillis() / 1000.0)
        }.toString()), ttl = 1)
        onMemberUpdate()
        emitStatus("عضو تأیید شد: $nick")
        try {
            com.ghostmago.app.data.AuditLog.append(context, "JOIN_OK", IdentityPolicy.sanitizeLog(nick))
        } catch (_: Exception) {}
    }

    fun sendRemoteWipe(targetHash: String = "*", reason: String = "remote") {
        if (!role.canApprove() && !hostMode) {
            emitStatus("فقط فرمانده می‌تواند پاکسازی از راه دور بفرستد")
            return
        }
        val payload = JSONObject().apply {
            put("cmd", "WIPE")
            put("reason", reason)
            put("nick", nickname)
            put("ts", System.currentTimeMillis() / 1000.0)
            put("sig", com.ghostmago.app.crypto.CryptoEngine.sha256(
                ("WIPE|" + groupId + "|" + reason).toByteArray()
            ).take(8).joinToString("") { "%02x".format(it) })
        }
        sendPacket(MeshPacket.TYPE_REMOTE_WIPE, targetHash, encryptControl(MeshPacket.TYPE_REMOTE_WIPE, payload), ttl = 6)
        emitStatus("دستور پاکسازی از راه دور ارسال شد")
    }

    fun rejectMember(originHash: String) {
        if (!role.canApprove() && !hostMode) return
        pending.remove(originHash)
        val payload = JSONObject().apply {
            put("reason", "رد شد"); put("ts", System.currentTimeMillis() / 1000.0)
        }
        sendPacket(MeshPacket.TYPE_JOIN_REJECT, originHash, encryptControl(MeshPacket.TYPE_JOIN_REJECT, payload), ttl = 8)
        onMemberUpdate()
    }

    
    private fun sendAck(msgId: String, toHash: String) {
        val payload = org.json.JSONObject().apply {
            put("ack", msgId)
            put("nick", nickname)
            put("ts", System.currentTimeMillis() / 1000.0)
        }
        sendPacket(MeshPacket.TYPE_ACK, toHash, encryptControl(MeshPacket.TYPE_ACK, payload), ttl = 4)
    }

    fun sendPrivate(peerHash: String, text: String): Boolean {
        val ct = PrivateSessionStore.encrypt(peerHash, text) ?: return false
        val b64 = android.util.Base64.encodeToString(ct, android.util.Base64.NO_WRAP)
        val payload = org.json.JSONObject().apply {
            put("nick", nickname)
            put("role", role.value)
            put("ct", b64)
            put("ts", System.currentTimeMillis() / 1000.0)
        }
        sendPacket(MeshPacket.TYPE_PRIVATE, peerHash, encryptPayload(payload.toString()))
        return true
    }

    fun sendX3DHBundle(peerHash: String = "") {
        val payload = org.json.JSONObject().apply {
            put("nick", nickname)
            put("role", role.value)
            put("bundle", com.ghostmago.app.mesh.PrivateSessionStore.myBundleJson())
            put("ts", System.currentTimeMillis() / 1000.0)
        }
        sendPacket(MeshPacket.TYPE_X3DH, peerHash, encryptPayload(payload.toString()))
    }

    fun sendPrivHandshake(peerHash: String, staticPub: ByteArray, ephPub: ByteArray) {
        val payload = org.json.JSONObject().apply {
            put("nick", nickname)
            put("role", role.value)
            put("static", android.util.Base64.encodeToString(staticPub, android.util.Base64.NO_WRAP))
            put("eph", android.util.Base64.encodeToString(ephPub, android.util.Base64.NO_WRAP))
            put("ts", System.currentTimeMillis() / 1000.0)
        }
        sendPacket(MeshPacket.TYPE_PRIV_HS, peerHash, encryptPayload(payload.toString()))
    }

    private fun encryptPayload(json: String): ByteArray = sessionCrypto.encryptPayload(json)

    private fun encryptControl(type: Int, json: JSONObject): ByteArray {
        try {
            if (AppState.requireControlSignatures) {
                ControlPlane.signPayload(groupId, groupPassword, type, json)
            }
        } catch (_: Exception) {}
        return encryptPayload(json.toString())
    }


    private fun decryptPayload(data: ByteArray): JSONObject? = sessionCrypto.decryptPayload(data)

    private fun sendPacket(type: Int, target: String, payload: ByteArray, ttl: Int = DEFAULT_TTL): String {
        if (!running || socket == null || payload.isEmpty()) return ""
        if (AppState.radioSilence && type == MeshPacket.TYPE_BEACON) return ""
        if (type != MeshPacket.TYPE_BEACON && type != MeshPacket.TYPE_COVER) {
            recentRealSends.incrementAndGet()
        }
        // Read-only roles cannot inject group traffic
        if (type in listOf(
                MeshPacket.TYPE_BROADCAST, MeshPacket.TYPE_DATA, MeshPacket.TYPE_VOICE,
                MeshPacket.TYPE_FILE, MeshPacket.TYPE_IMAGE, MeshPacket.TYPE_CHUNK
            ) && target.isEmpty() && !AppState.canSendGroup()
        ) {
            emitStatus("نقش شما فقط‌خواندنی است — ارسال گروهی مجاز نیست")
            return ""
        }
        val msgId = MeshPacket.makeMsgId(myHash)
        val packet = MeshPacket.build(type, ttl, msgId, myHash, target, 0, payload)
        if (type in listOf(MeshPacket.TYPE_DATA, MeshPacket.TYPE_PRIVATE, MeshPacket.TYPE_VOICE, MeshPacket.TYPE_FILE, MeshPacket.TYPE_IMAGE, MeshPacket.TYPE_CHUNK)
            && target.isNotEmpty() && target != "*"
        ) {
            pendingAcks[msgId] = System.currentTimeMillis()
        }
        // Control plane: join/ack must be instant so commander sees Approve buttons immediately
        val urgent = type in listOf(
            MeshPacket.TYPE_JOIN_REQ, MeshPacket.TYPE_JOIN_ACCEPT, MeshPacket.TYPE_JOIN_REJECT,
            MeshPacket.TYPE_ACK, MeshPacket.TYPE_BEACON, MeshPacket.TYPE_PRIV_HS, MeshPacket.TYPE_X3DH
        )
        scope.launch {
            if (AppState.randomSendDelay && !urgent && type != MeshPacket.TYPE_COVER) {
                delay(kotlin.random.Random.nextLong(50, 400))
            }
            try {
                val isCtrl = ControlPlane.isControl(type)
                val dataSock = socket ?: return@launch
                val ctrlSock = controlSocket
                val sock = if (isCtrl && AppState.separateControlPlane && ctrlSock != null) ctrlSock else dataSock
                val destPort = if (isCtrl && AppState.separateControlPlane) ControlPlane.controlPort(currentPort()) else currentPort()
                try { CoverTrafficProfile.noteRealPacket(packet.size) } catch (_: Exception) {}
                // Unicast if we know peer IP (Wi-Fi Direct / same LAN neighbor)
                val known = if (target.isNotEmpty() && target != "*") neighbors[target] else null
                if (known != null && known.ip != "?" && known.ip.isNotBlank()) {
                    try {
                        sock.send(DatagramPacket(packet, packet.size, InetAddress.getByName(known.ip), destPort))
                    } catch (_: Exception) {
                        sock.send(DatagramPacket(packet, packet.size, InetAddress.getByName("255.255.255.255"), destPort))
                    }
                } else {
                    // Broadcast + opportunistic unicast to known peers (OEM-friendlier)
                    sock.send(DatagramPacket(packet, packet.size, InetAddress.getByName("255.255.255.255"), destPort))
                    for ((_, ninfo) in neighbors) {
                        if (ninfo.ip.isNotBlank() && ninfo.ip != "?") {
                            try {
                                sock.send(DatagramPacket(packet, packet.size, InetAddress.getByName(ninfo.ip), destPort))
                            } catch (_: Exception) {}
                        }
                    }
                    if (target.isNotEmpty() && target != "*" && known == null &&
                        type in listOf(MeshPacket.TYPE_DATA, MeshPacket.TYPE_PRIVATE, MeshPacket.TYPE_VOICE, MeshPacket.TYPE_FILE, MeshPacket.TYPE_IMAGE)
                    ) {
                        storeForward.getOrPut(target) { mutableListOf() }.add(packet)
                        val q = storeForward[target]!!
                        while (q.size > AppState.offlineQueueMax.coerceIn(5, 100)) q.removeAt(0)
                        // expire packets older than 10 min marked by parallel map if present
                        emitStatus("صف آفلاین: پیام ذخیره شد (منتظر برگشت همسال)")
                    }
                }
                if (TransportProfile.allowWifi()) {
                    try { internetRelay?.send(packet) } catch (_: Exception) {}
                }
                if (TransportProfile.allowBluetooth()) {
                    try { btBridge?.send(packet) } catch (_: Exception) {}
                }
                if (TransportProfile.allowUsbRadio()) {
                    try { usbRadio?.send(packet) } catch (_: Exception) {}
                }
            } catch (e: Exception) { Log.e(TAG, "Send failed", e) }
        }
        return msgId
    }

    private suspend fun recvLoop() {
        val buf = ByteArray(MAX_PACKET)
        while (running) {
            try {
                val dp = DatagramPacket(buf, buf.size)
                socket?.receive(dp)
                handlePacket(dp.data.copyOf(dp.length), dp.address.hostAddress ?: "?")
            } catch (_: java.net.SocketTimeoutException) {
            } catch (e: Exception) {
                if (running) delay(30)
            }
        }
    }

    private fun handlePacket(data: ByteArray, ip: String) {
        if (data.isEmpty() || data.size > MAX_PACKET + 4096) return
        val p = MeshPacket.parse(data) ?: return
        if (p.originHash == myHash) return
        val now = System.currentTimeMillis()
                val skew = if (ControlPlane.isControl(p.type)) AppState.controlAntiReplaySec else AppState.antiReplayWindowSec
        if (kotlin.math.abs(now / 1000.0 - p.timestamp) > skew) return
        // stronger anti-replay key includes type
        val seenKey = p.msgId + ":" + p.type
        if (seen.putIfAbsent(seenKey, now) != null) return
        if (p.type == MeshPacket.TYPE_COVER) return

        val json = decryptPayload(p.payload) ?: return
        if (ControlPlane.isControl(p.type) && AppState.requireControlSignatures) {
            try {
                val verr = ControlPlane.verifyPayload(groupId, groupPassword, p.type, json, AppState.controlAntiReplaySec.toLong())
                if (verr != null) {
                    emitStatus("بسته کنترل رد شد: $verr")
                    return
                }
            } catch (_: Exception) {
                emitStatus("بسته کنترل: خطای تأیید امضا")
                return
            }
        }
        val nick = json.optString("nick", "?")
        neighbors[p.originHash] = NeighborInfo(nick, now, ip, p.hops + 1)
        members[p.originHash]?.let { info ->
            members[p.originHash] = info.copy(lastSeen = now, nick = nick.ifBlank { info.nick })
        }
        // Protocol / app version check
        val remoteVer = json.optString("appVer", "")
        if (remoteVer.isNotBlank() && remoteVer != com.ghostmago.app.BuildConfig.VERSION_NAME) {
            if (versionWarned.add(p.originHash)) {
                emitStatus("هشدار نسخه: $nick روی $remoteVer است — شما ${com.ghostmago.app.BuildConfig.VERSION_NAME}")
            }
            // Hard block private-like types if versions differ
            if (p.type in listOf(
                    MeshPacket.TYPE_PRIVATE, MeshPacket.TYPE_PRIV_HS, MeshPacket.TYPE_X3DH
                )
            ) {
                emitStatus("چت خصوصی با نسخه ناهمسان مسدود شد")
                return
            }
        }
        flushStoreForward(p.originHash)

        when (p.type) {
            MeshPacket.TYPE_BEACON -> {
                try {
                    val pe = json.optInt("epoch", -1)
                    if (pe >= 0) sessionCrypto.setEpoch(pe)
                } catch (_: Exception) {}
                if (members.containsKey(p.originHash)) {
                    val prev = members[p.originHash]!!
                    members[p.originHash] = prev.copy(lastSeen = now, nick = nick.ifBlank { prev.nick })
                    onMemberUpdate()
                } else if (!pending.containsKey(p.originHash) && p.originHash != myHash) {
                    pending[p.originHash] = MemberInfo(nick.ifBlank { "کاربر" }, json.optString("role", "recruit"), now, now)
                    if (role.canApprove() || hostMode) {
                        scope.launch(Dispatchers.Main) {
                            onJoinReq(p.originHash, nick.ifBlank { "کاربر" }, json.optString("role", "recruit"))
                        }
                    }
                    onMemberUpdate()
                } else {
                    pending[p.originHash]?.let {
                        pending[p.originHash] = it.copy(lastSeen = now, nick = nick.ifBlank { it.nick })
                    }
                }
            }
            MeshPacket.TYPE_JOIN_REQ -> {
                if ((role.canApprove() || hostMode) && p.originHash != myHash) {
                    val r = json.optString("role", "recruit")
                    val safeNick = try {
                        IdentityPolicy.sanitizeAlias(nick.ifBlank { "OP" })
                    } catch (_: Exception) {
                        nick.ifBlank { "OP" }
                    }
                    // Org trust checks (cert + invite + CRL)
                    var trustNote = ""
                    try {
                        if (json.has("cert")) {
                            val cert = OrgTrust.MemberCert.fromJson(json.getJSONObject("cert"))
                            val err = OrgTrust.verifyCertificate(context, cert)
                            if (err != null) {
                                emitStatus("رد اعتماد: $err ($safeNick)")
                                // still show pending but flag
                                trustNote = "⚠$err"
                            } else {
                                trustNote = "✓گواهی"
                            }
                        }
                        if (json.has("invite")) {
                            val invO = json.getJSONObject("invite")
                            val inv = OrgTrust.InviteToken(
                                tokenId = invO.getString("tid"),
                                groupId = invO.optString("gid", groupId),
                                role = invO.optString("role", r),
                                expiresAt = invO.getLong("exp"),
                                maxUses = invO.optInt("uses", 1),
                                caId = invO.getString("caId"),
                                signatureB64 = invO.getString("sig")
                            )
                            val ierr = OrgTrust.verifyInvite(context, inv)
                            if (ierr != null) {
                                emitStatus("دعوت نامعتبر: $ierr")
                                trustNote = (trustNote + " ⚠دعوت").trim()
                            } else {
                                trustNote = (trustNote + " ✓دعوت").trim()
                                try { OrgTrust.consumeInvite(context, inv.tokenId) } catch (_: Exception) {}
                            }
                        }
                    } catch (e: Exception) {
                        trustNote = "⚠اعتماد"
                    }
                    val display = if (trustNote.isNotEmpty()) "$safeNick $trustNote" else safeNick
                    pending[p.originHash] = MemberInfo(display, r, now, now)
                    scope.launch(Dispatchers.Main) {
                        onJoinReq(p.originHash, display, r)
                    }
                    onMemberUpdate()
                } else if (p.ttl > 1) {
                    relay(p)
                }
            }
            MeshPacket.TYPE_JOIN_ACCEPT -> {
                if (p.targetHash == myHash) {
                    membershipAccepted = true
                    members[myHash] = MemberInfo(nickname, json.optString("role", role.value), now, now)
                    onMemberUpdate()
                    emitStatus("عضویت تأیید شد — به شبکه متصل شدید")
                    scope.launch(Dispatchers.Main) {
                        onJoinAccepted(json.optString("role", role.value))
                    }
                }
            }
            MeshPacket.TYPE_REMOTE_WIPE -> {
                val cmd = json.optString("cmd", "")
                if (cmd == "WIPE") {
                    // Target me or broadcast from commander-rank peer
                    val forMe = p.targetHash.isEmpty() || p.targetHash == "*" || p.targetHash == myHash
                    if (forMe) {
                        emitStatus("دستور پاکسازی از راه دور دریافت شد")
                        scope.launch(Dispatchers.Main) {
                            try {
                                com.ghostmago.app.util.SecurityUtils.panicWipe(context)
                            } catch (_: Exception) {}
                        }
                    } else if (p.ttl > 1) relay(p)
                }
            }
            MeshPacket.TYPE_JOIN_REJECT -> {
                if (p.targetHash == myHash) {
                    membershipAccepted = false
                    emitStatus("عضویت رد شد: ${json.optString("reason", "بدون توضیح")}")
                }
            }
            MeshPacket.TYPE_DATA, MeshPacket.TYPE_BROADCAST -> {
                if (p.targetHash.isEmpty() || p.targetHash == "*" || p.targetHash == myHash) {
                    var origin = p.originHash
                    var displayNick = nick
                    var text = json.optString("text", "")
                    val eph = json.optInt("ephemeral", 0)
                    if (json.optBoolean("sealed", false)) {
                        try {
                            val blob = android.util.Base64.decode(json.optString("blob"), android.util.Base64.NO_WRAP)
                            val opened = SealedSender.open(blob, groupPassword)
                            if (opened != null) {
                                origin = opened.originHash
                                displayNick = opened.nick
                                val inner = decryptPayload(opened.payload)
                                text = inner?.optString("text", "") ?: ""
                            }
                        } catch (_: Exception) {}
                    }
                    if (text.isNotEmpty()) {
                        scope.launch(Dispatchers.Main) {
                            onText(origin, displayNick, text, p.hops, eph)
                            MeshNotifier.notifyMessage(context, displayNick, text.take(80))
                        }
                        sendAck(p.msgId, origin)
                    }
                } else if (p.ttl > 1) relay(p)
            }
            MeshPacket.TYPE_IMAGE -> {
                if (p.targetHash.isEmpty() || p.targetHash == myHash) {
                    val b64 = json.optString("img", "")
                    if (b64.isNotEmpty()) {
                        val jpeg = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                        val cap = json.optString("cap", "")
                        scope.launch(Dispatchers.Main) { onImage(p.originHash, nick, jpeg, cap, p.hops) }
                        sendAck(p.msgId, p.originHash)
                    }
                } else if (p.ttl > 1) relay(p)
            }
            MeshPacket.TYPE_VOICE -> {
                if (p.targetHash.isEmpty() || p.targetHash == myHash || p.targetHash == "*") {
                    val b64 = json.optString("audio", "")
                    if (b64.isNotEmpty()) {
                        val audio = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                        val isPriv = json.optBoolean("priv", p.targetHash == myHash)
                        val isCall = json.optBoolean("call", false)
                        val display = when {
                            isCall -> "📞 $nick"
                            isPriv -> "🔒 $nick"
                            else -> nick
                        }
                        scope.launch(Dispatchers.Main) { onVoice(p.originHash, display, audio, p.hops) }
                    }
                } else if (p.ttl > 1) relay(p)
            }
            MeshPacket.TYPE_FILE -> {
                if (p.targetHash.isEmpty() || p.targetHash == myHash) {
                    val filename = json.optString("filename", "file")
                    val b64 = json.optString("data", "")
                    if (b64.isNotEmpty()) {
                        val fileData = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                        scope.launch(Dispatchers.Main) { onFile(p.originHash, nick, filename, fileData, p.hops) }
                    }
                } else if (p.ttl > 1) relay(p)
            }

            MeshPacket.TYPE_CHUNK -> {
                if (p.targetHash.isEmpty() || p.targetHash == "*" || p.targetHash == myHash) {
                    val fid = json.optString("fid", "")
                    val idx = json.optInt("i", -1)
                    val total = json.optInt("n", 0)
                    val filename = json.optString("filename", "file.bin")
                    val b64 = json.optString("data", "")
                    if (fid.isNotEmpty() && idx >= 0 && total > 0 && b64.isNotEmpty()) {
                        val arr = chunkBuffers.getOrPut(fid) { arrayOfNulls(total) }
                        if (idx < arr.size) {
                            arr[idx] = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                            chunkMeta[fid] = filename to nick
                        }
                        if (arr.all { it != null }) {
                            val full = arr.filterNotNull().fold(ByteArray(0)) { a, b -> a + b }
                            chunkBuffers.remove(fid)
                            val meta = chunkMeta.remove(fid)
                            val fn = meta?.first ?: filename
                            val nk = meta?.second ?: nick
                            scope.launch(Dispatchers.Main) { onFile(p.originHash, nk, fn, full, p.hops) }
                            emitStatus("فایل کامل دریافت شد: $fn (${full.size} بایت)")
                            sendAck(p.msgId, p.originHash)
                        } else {
                            val got = arr.count { it != null }
                            emitStatus("تکه $got/$total از $filename")
                        }
                    }
                } else if (p.ttl > 1) relay(p)
            }

            MeshPacket.TYPE_PRIVATE -> {
                if (p.targetHash == myHash) {
                    val signal = json.optString("callSignal", "")
                    if (signal.isNotEmpty()) {
                        val extra = JSONObject().apply {
                            put("sdpType", json.optString("sdpType", ""))
                            put("sdp", json.optString("sdp", ""))
                            put("iceMid", json.optString("iceMid", ""))
                            put("iceIndex", json.optInt("iceIndex", 0))
                            put("ice", json.optString("ice", ""))
                        }.toString()
                        scope.launch(Dispatchers.Main) {
                            onCallSignal(p.originHash, nick.ifBlank { "کاربر" }, signal, extra)
                            val msg = when (signal) {
                                "ring" -> "📞 تماس ورودی از $nick"
                                "accept" -> "تماس پذیرفته شد"
                                "end" -> "تماس پایان یافت"
                                "busy" -> "مشغول"
                                "sdp", "ice" -> ""
                                else -> "سیگنال تماس: $signal"
                            }
                            if (msg.isNotEmpty()) {
                                onPrivate(p.originHash, nick, msg)
                                emitStatus(msg)
                            }
                        }
                    } else {
                        val b64 = json.optString("ct", "")
                        if (b64.isNotEmpty()) {
                            if (PrivateSessionStore.get(p.originHash) == null) {
                                try {
                                    PrivateSessionStore.startSymmetric(myHash, p.originHash, nick, groupPassword)
                                } catch (_: Exception) {}
                            }
                            try {
                                val ct = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                                val plain = PrivateSessionStore.decrypt(p.originHash, ct)
                                if (plain != null) {
                                    scope.launch(Dispatchers.Main) { onPrivate(p.originHash, nick, plain) }
                                    sendAck(p.msgId, p.originHash)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                } else if (p.ttl > 1) relay(p)
            }
            MeshPacket.TYPE_ACK -> {
                if (p.targetHash == myHash || p.targetHash.isEmpty()) {
                    val ackId = json.optString("ack", p.msgId)
                    pendingAcks.remove(ackId)
                    delivered.add(ackId)
                    scope.launch(Dispatchers.Main) { onDelivered(ackId) }
                }
            }
            MeshPacket.TYPE_X3DH -> {
                val bundleJson = json.optString("bundle", "")
                if (bundleJson.isNotEmpty()) {
                    val bundle = PrivateSessionStore.parseBundle(bundleJson)
                    if (bundle != null && (p.targetHash.isEmpty() || p.targetHash == myHash)) {
                        try {
                            // Alice path if we don't have session yet or upgrade
                            if (PrivateSessionStore.get(p.originHash) == null) {
                                val (sess, eph) = PrivateSessionStore.startX3DHAlice(myHash, p.originHash, nick, bundle)
                                // send eph back as PRIV_HS style for Bob
                                val resp = org.json.JSONObject().apply {
                                    put("nick", nickname)
                                    put("ik", android.util.Base64.encodeToString(
                                        com.ghostmago.app.crypto.X3DH.bundle(PrivateSessionStore.local()).identityPub,
                                        android.util.Base64.NO_WRAP))
                                    put("eph", android.util.Base64.encodeToString(eph, android.util.Base64.NO_WRAP))
                                    put("ts", System.currentTimeMillis() / 1000.0)
                                }
                                sendPacket(MeshPacket.TYPE_PRIV_HS, p.originHash, encryptPayload(resp.toString()))
                            }
                        } catch (_: Exception) {
                            // fallback ignore
                        }
                    }
                }
            }
            MeshPacket.TYPE_PRIV_HS -> {
                if (p.targetHash == myHash) {
                    val st = json.optString("static", json.optString("ik", ""))
                    val ep = json.optString("eph", "")
                    scope.launch(Dispatchers.Main) { onPrivHs(p.originHash, nick, st, ep) }
                } else if (p.ttl > 1) relay(p)
            }
        }
    }

    private fun flushStoreForward(peerHash: String) {
        val list = storeForward.remove(peerHash) ?: return
        val info = neighbors[peerHash] ?: return
        scope.launch {
            for (packet in list) {
                try {
                    socket?.send(DatagramPacket(packet, packet.size, InetAddress.getByName(info.ip), currentPort()))
                    delay(300)
                } catch (_: Exception) {}
            }
            if (list.isNotEmpty()) emitStatus("تحویل ${list.size} پیام ذخیرهشده")
        }
    }

    private fun relay(p: MeshPacket.Parsed) {
        val newTtl = p.ttl - 1
        val newHops = p.hops + 1
        if (newTtl <= 0 || newHops > 7) return
        val packet = MeshPacket.build(p.type, newTtl, p.msgId, p.originHash, p.targetHash, newHops, p.payload, p.flags)
        // Multi-hop store-and-forward: keep copy for target if not online here
        if (p.targetHash.isNotEmpty() && p.targetHash != "*" && !neighbors.containsKey(p.targetHash)
            && p.type in listOf(MeshPacket.TYPE_DATA, MeshPacket.TYPE_PRIVATE, MeshPacket.TYPE_VOICE, MeshPacket.TYPE_FILE)
        ) {
            storeForward.getOrPut(p.targetHash) { mutableListOf() }.add(packet)
            if (storeForward[p.targetHash]!!.size > 30) storeForward[p.targetHash]!!.removeAt(0)
        }
        try {
            val known = neighbors[p.targetHash]
            if (known != null && known.ip.isNotBlank() && known.ip != "?") {
                socket?.send(DatagramPacket(packet, packet.size, InetAddress.getByName(known.ip), currentPort()))
            } else {
                socket?.send(DatagramPacket(packet, packet.size, InetAddress.getByName("255.255.255.255"), currentPort()))
            }
        } catch (_: Exception) {}
    }

    private suspend fun joinRetryLoop() {
        var tries = 0
        emitStatus("در انتظار تأیید فرمانده...")
        // First request immediately (no wait), then fast retries so commander UI appears quickly
        while (running && !membershipAccepted && tries < 50) {
            try {
                sendJoinRequest()
                emitStatus("درخواست عضویت ارسال شد — منتظر تأیید فرمانده (${tries + 1})")
            } catch (_: Exception) {
            }
            // 1s for first 10 tries, then 2.5s
            delay(if (tries < 5) 1200L else if (tries < 15) 3000L else 6000L)
            tries++
        }
        if (running && !membershipAccepted) {
            emitStatus("فرمانده پاسخ نداد — درخواست تکرار نشد")
        }
    }

    private suspend fun beaconLoop() {
        while (running) {
            val interval = try {
                MeshHealth.beaconIntervalMs(context)
            } catch (_: Exception) {
                BEACON_INTERVAL
            }
            delay(interval + Random.nextLong(-800, 800))
            if (AppState.radioSilence) continue
            try {
                if (AppState.groupEpochAutoRotate) sessionCrypto.maybeAutoRotate()
            } catch (_: Exception) {}
            val payload = JSONObject().apply {
                put("nick", nickname); put("role", role.value)
                put("appVer", com.ghostmago.app.BuildConfig.VERSION_NAME)
                put("proto", 6)
                put("ts", System.currentTimeMillis() / 1000.0)
                put("epoch", sessionCrypto.currentEpoch())
            }
            sendPacket(MeshPacket.TYPE_BEACON, "", encryptPayload(payload.toString()), ttl = 1)
        }
    }

    private suspend fun coverLoop() {
        while (running) {
            // Adaptive: more real traffic → less cover interval stretch; idle → denser cover
            val base = AppState.coverIntervalSec.coerceIn(20, 180)
            val real = recentRealSends.getAndSet(0)
            val adaptSec = when {
                real >= 5 -> base * 2
                real >= 2 -> base
                else -> maxOf(5, base / 2)
            }
            delay(CoverTrafficProfile.nextDelayMs(adaptSec))
            if (!running || !AppState.coverTraffic || AppState.radioSilence) continue
            try {
                val sz = CoverTrafficProfile.nextCoverSize()
                val junk = ByteArray(sz)
                java.security.SecureRandom().nextBytes(junk)
                sendPacket(MeshPacket.TYPE_COVER, "", junk, ttl = 1)
            } catch (_: Exception) {}
        }
    }

    private suspend fun ackRetryLoop() {
        while (running) {
            delay(12000)
            val now = System.currentTimeMillis()
            for ((msgId, sentAt) in pendingAcks.toMap()) {
                if (now - sentAt > 25_000) {
                    pendingAcks.remove(msgId)
                    if (!delivered.contains(msgId)) {
                        scope.launch(Dispatchers.Main) { onDeliveryFailed(msgId) }
                    }
                }
            }
            if (neighbors.isNotEmpty() && storeForward.isNotEmpty()) {
                val sock = socket
                if (sock != null) {
                    for ((target, packets) in storeForward.toMap()) {
                        val n = neighbors[target]
                        if (n != null && n.ip.isNotBlank() && n.ip != "?") {
                            val copy = packets.toList()
                            storeForward.remove(target)
                            for (packet in copy) {
                                try {
                                    sock.send(java.net.DatagramPacket(packet, packet.size, java.net.InetAddress.getByName(n.ip), currentPort()))
                                } catch (_: Exception) {}
                            }
                            emitStatus("صف آفلاین ارسال شد: ${copy.size}")
                        }
                    }
                }
            }
        }
    }

    private suspend fun controlRecvLoop() {
        val buf = ByteArray(MAX_PACKET)
        while (running) {
            try {
                val dp = java.net.DatagramPacket(buf, buf.size)
                controlSocket?.receive(dp)
                handlePacket(dp.data.copyOf(dp.length), dp.address.hostAddress ?: "?")
            } catch (_: java.net.SocketTimeoutException) {
            } catch (e: Exception) {
                if (running) kotlinx.coroutines.delay(40)
            }
        }
    }

    private suspend fun cleanupLoop() {
        while (running) {
            delay(20_000)
            val now = System.currentTimeMillis()
            try {
                seen.entries.removeIf { now - it.value > 90_000 }
                // Cap seen map if flooded
                if (seen.size > 2000) {
                    val oldest = seen.entries.sortedBy { it.value }.take(seen.size - 1500)
                    oldest.forEach { seen.remove(it.key) }
                }
                neighbors.entries.removeIf { now - it.value.lastSeen > ONLINE_TIMEOUT * 3 }
                if (pendingAcks.size > 80) {
                    val old = pendingAcks.entries.sortedBy { it.value }.take(pendingAcks.size - 50)
                    old.forEach { pendingAcks.remove(it.key) }
                }
                // Drop expired offline queue entries is handled by size cap per peer
                for ((k, q) in storeForward.toMap()) {
                    if (q.isEmpty()) storeForward.remove(k)
                }
            } catch (_: Exception) {
            }
        }
    }
}
