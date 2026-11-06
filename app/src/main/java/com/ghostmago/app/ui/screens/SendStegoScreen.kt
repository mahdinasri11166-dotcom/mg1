package com.ghostmago.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostmago.app.stego.StegoEngine
import com.ghostmago.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendStegoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var cover by remember { mutableStateOf(StegoEngine.getDefaultCover(0)) }
    var secret by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var coverIndex by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ارسال پنهاننگاری متن", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = BgDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("متن ظاهر (کاور)", color = TextSecondary, fontSize = 13.sp)
            OutlinedTextField(
                value = cover,
                onValueChange = { cover = it },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                colors = fieldColors(),
                shape = RoundedCornerShape(12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 1, 2, 3).forEach { i ->
                    FilterChip(
                        selected = coverIndex == i,
                        onClick = {
                            coverIndex = i
                            cover = StegoEngine.getDefaultCover(i)
                        },
                        label = { Text("کاور ${i + 1}") }
                    )
                }
            }

            Text("متن مخفی", color = TextSecondary, fontSize = 13.sp)
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                colors = fieldColors(),
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("پیام واقعی که باید مخفی شود…", color = TextSecondary) }
            )

            Text("رمز عبور", color = TextSecondary, fontSize = 13.sp)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )

            if (error.isNotEmpty()) {
                Text(error, color = Danger, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    error = ""
                    result = ""
                    try {
                        if (secret.isBlank() || password.isBlank()) {
                            error = "متن مخفی و رمز را وارد کنید"
                            return@Button
                        }
                        result = StegoEngine.encode(cover, secret, password)
                        com.ghostmago.app.util.SecurityUtils.saveStat(context, "sent")
                        Toast.makeText(context, "آماده شد — کپی کنید", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        error = e.message ?: "خطا"
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ساخت پنهاننگاری متن", fontWeight = FontWeight.Bold)
            }

            if (result.isNotEmpty()) {
                Text("نتیجه (قابل ارسال با SMS / تلگرام / واتساپ)", color = Secondary, fontSize = 13.sp)
                OutlinedTextField(
                    value = result,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    colors = fieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        try {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("stego", result))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Secondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }
                Button(
                    onClick = {
                        com.ghostmago.app.util.ShareHelper.shareText(context, result, "پنهاننگاری")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Share") }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = Border,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Primary,
    focusedContainerColor = CardDark,
    unfocusedContainerColor = CardDark
)
