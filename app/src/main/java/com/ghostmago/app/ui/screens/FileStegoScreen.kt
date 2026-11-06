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
import com.ghostmago.app.stego.FileStego
import com.ghostmago.app.ui.theme.*
import com.ghostmago.app.util.ShareHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileStegoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf("hide") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var coverBytes by remember { mutableStateOf<ByteArray?>(null) }
    var fileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var fileName by remember { mutableStateOf("payload.bin") }

    val coverPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            coverBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            status = "تصویر پوششی انتخاب شد (${coverBytes?.size ?: 0} بایت)"
        } catch (e: Exception) {
            status = e.message ?: "خطا"
        }
    }
    val filePick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "payload.bin"
            fileBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            status = "فایل انتخاب شد: $fileName (${fileBytes?.size ?: 0} بایت)"
        } catch (e: Exception) {
            status = e.message ?: "خطا"
        }
    }
    val stegoPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (password.length < 4) {
            Toast.makeText(context, "رمز حداقل ۴ کاراکتر", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        try {
            val stego = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
            val plain = FileStego.extractFromPng(stego, password)
            val out = File(context.cacheDir, "extracted_${System.currentTimeMillis()}.bin")
            out.writeBytes(plain)
            status = "استخراج شد: ${out.name} (${plain.size} بایت)"
            ShareHelper.shareFile(context, out, "application/octet-stream")
        } catch (e: Exception) {
            status = "خطا: ${e.message}"
            Toast.makeText(context, status, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پنهاننگاری فایل در تصویر", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = BgDark
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("هر فایل (PDF، ZIP، …) با AES-256 رمز و داخل PNG پنهان میشود.", color = TextSecondary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "hide", onClick = { mode = "hide" }, label = { Text("پنهانسازی") })
                FilterChip(selected = mode == "extract", onClick = { mode = "extract" }, label = { Text("استخراج") })
            }
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("رمز فایل") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary, unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )
            if (mode == "hide") {
                Button(onClick = { coverPick.launch("image/*") }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Secondary), shape = RoundedCornerShape(12.dp)) {
                    Text("انتخاب تصویر پوششی (PNG/JPG)")
                }
                Button(onClick = { filePick.launch("*/*") }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent), shape = RoundedCornerShape(12.dp)) {
                    Text("انتخاب فایل برای پنهان کردن")
                }
                Button(
                    onClick = {
                        try {
                            val cover = coverBytes ?: run {
                                Toast.makeText(context, "تصویر پوششی لازم است", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val file = fileBytes ?: run {
                                Toast.makeText(context, "فایل لازم است", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (password.length < 4) {
                                Toast.makeText(context, "رمز کوتاه است", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val outBytes = FileStego.hideInPng(cover, file, password)
                            val out = File(context.cacheDir, "stego_file_${System.currentTimeMillis()}.png")
                            out.writeBytes(outBytes)
                            status = "ساخته شد: ${out.name} (${outBytes.size} بایت)"
                            ShareHelper.shareFile(context, out, "image/png")
                        } catch (e: Exception) {
                            status = e.message ?: "خطا"
                            Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("رمزنگاری و پنهانسازی", fontWeight = FontWeight.Bold) }
            } else {
                Button(
                    onClick = { stegoPick.launch("image/*") },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("انتخاب تصویر و استخراج فایل") }
            }
            if (status.isNotBlank()) Text(status, color = Secondary, fontSize = 13.sp)
        }
    }
}
