package com.ghostmago.app.ui.screens.mesh

import java.util.UUID

/** Lightweight chat item — large byte arrays should be trimmed by the screen. */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val origin: String,
    val nick: String,
    val text: String = "",
    val isVoice: Boolean = false,
    val isFile: Boolean = false,
    val filename: String = "",
    val audioData: ByteArray? = null,
    val fileData: ByteArray? = null,
    val hops: Int = 0,
    val isMine: Boolean = false,
    val ephemeralSec: Int = 0,
    val classification: String = "public",
    val deliveryStatus: String = "",
    val msgId: String = "",
    val imageBytes: ByteArray? = null,
    val time: Long = System.currentTimeMillis()
)
