package com.ghostmago.app.mesh

import android.content.Context
import com.ghostmago.app.data.MeshRole
import com.ghostmago.app.util.VoiceManager

/**
 * Lazy mesh facade — networking is created only when the user opens
 * the mesh screen, not at application startup.
 */
object MeshModule {
    @Volatile
    private var active: MeshNetwork? = null

    fun current(): MeshNetwork? = active

    fun bind(net: MeshNetwork) {
        active = net
    }

    fun unbind(net: MeshNetwork?) {
        if (active === net) active = null
    }

    fun create(
        context: Context,
        myId: String,
        nickname: String,
        groupId: String,
        groupPassword: String,
        role: MeshRole,
        hostMode: Boolean = true,
        onText: (origin: String, nick: String, text: String, hops: Int, ephemeralSec: Int) -> Unit,
        onVoice: (origin: String, nick: String, audio: ByteArray, hops: Int) -> Unit,
        onFile: (origin: String, nick: String, filename: String, data: ByteArray, hops: Int) -> Unit,
        onImage: (origin: String, nick: String, jpeg: ByteArray, caption: String, hops: Int) -> Unit = { _, _, _, _, _ -> },
        onDelivered: (msgId: String) -> Unit = {},
        onDeliveryFailed: (msgId: String) -> Unit = {},
        onJoinReq: (origin: String, nick: String, role: String) -> Unit = { _, _, _ -> },
        onPrivate: (origin: String, nick: String, text: String) -> Unit = { _, _, _ -> },
        onPrivHs: (origin: String, nick: String, staticPubB64: String, ephPubB64: String) -> Unit = { _, _, _, _ -> },
        onMemberUpdate: () -> Unit = {},
        onStatus: (String) -> Unit = {},
        onJoinAccepted: (role: String) -> Unit = {},
        onCallSignal: (origin: String, nick: String, action: String, extra: String) -> Unit = { _, _, _, _ -> }
    ): MeshNetwork {
        val net = MeshNetwork(
            context = context,
            myId = myId,
            nickname = nickname,
            groupId = groupId,
            groupPassword = groupPassword,
            role = role,
            hostMode = hostMode,
            onText = onText,
            onVoice = onVoice,
            onFile = onFile,
            onImage = onImage,
            onDelivered = onDelivered,
            onDeliveryFailed = onDeliveryFailed,
            onJoinReq = onJoinReq,
            onPrivate = onPrivate,
            onPrivHs = onPrivHs,
            onMemberUpdate = onMemberUpdate,
            onStatus = onStatus,
            onJoinAccepted = onJoinAccepted,
            onCallSignal = onCallSignal
        )
        bind(net)
        return net
    }

    fun createVoiceManager(context: Context): VoiceManager = VoiceManager(context)
}
