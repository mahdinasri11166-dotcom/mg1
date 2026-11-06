package com.ghostmago.app.ui.screens.mesh

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostmago.app.ui.theme.CardDark
import com.ghostmago.app.ui.theme.Danger
import com.ghostmago.app.ui.theme.Primary
import com.ghostmago.app.ui.theme.Secondary
import com.ghostmago.app.ui.theme.TextPrimary
import com.ghostmago.app.ui.theme.TextSecondary
import com.ghostmago.app.ui.theme.Warning
import com.ghostmago.app.util.VoiceManager
import java.text.SimpleDateFormat

@Composable
fun MeshBubble(
    msg: ChatMessage,
    timeFmt: SimpleDateFormat,
    voiceManager: VoiceManager
) {
    val align = if (msg.isMine) Alignment.CenterEnd else Alignment.CenterStart
    val bg = if (msg.isMine) Primary.copy(alpha = 0.25f) else CardDark
    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bg),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Column(
                Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!msg.isMine) {
                    Text(msg.nick, color = Secondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                if (msg.imageBytes != null && (msg.imageBytes?.size ?: 0) < 2_000_000) {
                    val bmp = remember(msg.id) {
                        try {
                            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                            BitmapFactory.decodeByteArray(msg.imageBytes, 0, msg.imageBytes!!.size, opts)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "img",
                            modifier = Modifier.size(140.dp)
                        )
                    }
                }
                if (msg.isVoice && msg.audioData != null) {
                    TextButton(onClick = {
                        try {
                            voiceManager.playBytes(msg.audioData)
                        } catch (_: Exception) {
                        }
                    }) { Text("▶ پخش ویس", color = Primary) }
                }
                if (msg.isFile) {
                    Text("📎 ${msg.filename}", color = TextPrimary)
                }
                if (msg.text.isNotBlank()) {
                    Text(msg.text, color = TextPrimary, fontSize = 14.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(timeFmt.format(msg.time), color = TextSecondary, fontSize = 10.sp)
                    if (msg.hops > 0) {
                        Text("hop ${msg.hops}", color = TextSecondary, fontSize = 10.sp)
                    }
                    if (msg.isMine && msg.deliveryStatus.isNotEmpty()) {
                        val (label, col) = when (msg.deliveryStatus) {
                            "delivered" -> "رسیده ✓✓" to Secondary
                            "sent" -> "ارسال شد ✓" to TextSecondary
                            "failed" -> "ناموفق ✗" to Danger
                            "queued" -> "در صف…" to Warning
                            else -> msg.deliveryStatus to TextSecondary
                        }
                        Text(label, color = col, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
