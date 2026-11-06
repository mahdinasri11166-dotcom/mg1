package com.ghostmago.app.mesh

import com.ghostmago.app.ui.screens.mesh.ChatMessage
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Capped chat buffer — keeps UI and RAM light under heavy mesh traffic.
 */
class MeshMessageStore(private val maxSize: Int = 18) {
    private val items = CopyOnWriteArrayList<ChatMessage>()

    fun snapshot(): List<ChatMessage> = items.toList()

    fun add(msg: ChatMessage): List<ChatMessage> {
        items.add(msg)
        trim()
        return snapshot()
    }

    fun mapUpdate(transform: (ChatMessage) -> ChatMessage): List<ChatMessage> {
        // Single pass, avoid allocating intermediate lists
        val n = items.size
        for (i in 0 until n) {
            try {
                items[i] = transform(items[i])
            } catch (_: Exception) {
            }
        }
        return snapshot()
    }

    fun replaceAll(list: List<ChatMessage>): List<ChatMessage> {
        items.clear()
        if (list.isNotEmpty()) {
            val start = (list.size - maxSize).coerceAtLeast(0)
            items.addAll(list.subList(start, list.size))
        }
        return snapshot()
    }

    private fun trim() {
        while (items.size > maxSize) {
            try {
                items.removeAt(0)
            } catch (_: Exception) {
                break
            }
        }
        // Keep media only on last 4 messages
        val keepFrom = (items.size - 4).coerceAtLeast(0)
        for (i in 0 until keepFrom) {
            val m = items[i]
            if (m.audioData != null || m.imageBytes != null || m.fileData != null) {
                items[i] = m.copy(
                    audioData = null,
                    imageBytes = null,
                    fileData = null,
                    text = m.text.ifBlank {
                        when {
                            m.isVoice -> "🎤 ویس"
                            m.isFile -> "📎 فایل"
                            else -> m.text
                        }
                    }
                )
            }
        }
    }

    fun clear() {
        items.clear()
    }
}
