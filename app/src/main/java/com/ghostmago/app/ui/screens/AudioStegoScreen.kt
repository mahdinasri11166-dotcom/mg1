package com.ghostmago.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostmago.app.crypto.CryptoEngine
import com.ghostmago.app.stego.AudioStego
import com.ghostmago.app.ui.theme.*
import com.ghostmago.app.util.SecurityUtils
import com.ghostmago.app.util.ShareHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioStegoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var outShareFile by remember { mutableStateOf<File?>(null) }
    var mode by remember { mutableStateOf("hide") }
    var secret by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var wavBytes by remember { mutableStateOf<ByteArray?>(null) }
    var wavName by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            wavBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            wavName = uri.lastPathSegment ?: "audio.wav"
            Toast.makeText(context, "File selected", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پنهاننگاری صوت (WAV)", color = TextPrimary) },
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
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "hide", onClick = { mode = "hide" }, label = { Text("پنهانسازی") })
                FilterChip(selected = mode == "extract", onClick = { mode = "extract" }, label = { Text("استخراج") })
            }
            Button(
                onClick = { picker.launch("audio/*") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
            ) { Text("انتخاب فایل WAV") }
            if (wavName.isNotBlank()) Text("File: $wavName", color = TextSecondary, fontSize = 12.sp)
            if (mode == "hide") {
                OutlinedTextField(
                    value = secret, onValueChange = { secret = it },
                    label = { Text("متن مخفی") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    colors = fColors(), shape = RoundedCornerShape(12.dp)
                )
            }
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("رمز") },
                modifier = Modifier.fillMaxWidth(),
                colors = fColors(), shape = RoundedCornerShape(12.dp)
            )
            Button(
                onClick = {
                    val data = wavBytes
                    if (data == null) {
                        result = "No WAV"
                        return@Button
                    }
                    try {
                        if (mode == "hide") {
                            val payload = CryptoEngine.encrypt(secret.toByteArray(Charsets.UTF_8), password.toCharArray())
                            val outBytes = AudioStego.hide(data, payload)
                            val outDir = File(context.filesDir, "stego").apply { mkdirs() }
                            val out = File(outDir, "audio_stego_${System.currentTimeMillis()}.wav")
                            out.writeBytes(outBytes)
                            outShareFile = out
                            result = "Saved: ${out.name}"
                            SecurityUtils.saveStat(context, "audio_sent")
                        } else {
                            val payload = AudioStego.extract(data)
                            val plain = CryptoEngine.decrypt(payload, password.toCharArray())
                            result = String(plain, Charsets.UTF_8)
                            SecurityUtils.saveStat(context, "audio_recv")
                        }
                    } catch (e: Exception) {
                        result = "Error: ${e.message}"
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Secondary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (mode == "hide") "پنهان در صوت" else "استخراج از صوت", fontWeight = FontWeight.Bold)
            }
            if (result.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(result, color = TextPrimary, modifier = Modifier.padding(16.dp))
                }
            }
            Button(
                onClick = {
                    val f = outShareFile
                    if (f != null && f.exists()) ShareHelper.shareFile(context, f, "audio/wav")
                    else Toast.makeText(context, "No output file", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(12.dp)
            ) { Text("اشتراک صوت") }
            Text("فقط فرمت WAV از نوع PCM", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun fColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary, unfocusedBorderColor = Border,
    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
    cursorColor = Primary, focusedContainerColor = CardDark, unfocusedContainerColor = CardDark,
    focusedLabelColor = Primary, unfocusedLabelColor = TextSecondary
)
