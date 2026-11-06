package com.ghostmago.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostmago.app.ui.theme.Accent
import com.ghostmago.app.ui.theme.BgDark
import com.ghostmago.app.ui.theme.Primary
import com.ghostmago.app.ui.theme.TextPrimary
import com.ghostmago.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onDone: (() -> Unit)? = null,
    loading: Boolean = false,
    message: String = "مشکات"
) {
    if (onDone != null && !loading) {
        LaunchedEffect(Unit) {
            delay(400)
            onDone()
        }
    }
    Box(
        Modifier.fillMaxSize().background(BgDark),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("مشکات", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("سازنده: Mr Elf", color = Accent, fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = Primary,
                    strokeWidth = 3.dp
                )
                Spacer(Modifier.height(12.dp))
                Text(message, color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
}
