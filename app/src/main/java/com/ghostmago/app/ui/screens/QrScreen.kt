package com.ghostmago.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostmago.app.crypto.CryptoEngine
import com.ghostmago.app.ui.theme.*
import com.ghostmago.app.util.QrHelper
import com.ghostmago.app.util.SecurityUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var outShareFile by remember { mutableStateOf<java.io.File?>(null) }
    var mode by remember { mutableStateOf("gen") }
    var text by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var qrBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var result by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openInputStream(uri)?.use {
                val bmp = BitmapFactory.decodeStream(it) ?: return@use
                val decoded = QrHelper.decode(bmp)
                if (decoded != null) {
                    if (password.isNotBlank()) {
                        try {
                            result = CryptoEngine.decryptString(decoded, password)
                            SecurityUtils.saveStat(context, "qr_read")
                        } catch (e: Exception) {
                            result = "خطا در رمزگشایی: ${e.message}"
                        }
                    } else {
                        result = decoded
                    }
                } else result = "QR پیدا نشد"
            }
        } catch (e: Exception) {
            result = "خطا: ${e.message}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("بارکد", color = TextPrimary) },
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
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "gen", onClick = { mode = "gen" }, label = { Text("تولید") })
                FilterChip(selected = mode == "read", onClick = { mode = "read" }, label = { Text("خواندن") })
            }

            if (mode == "gen") {
                OutlinedTextField(value = text, onValueChange = { text = it },
                    label = { Text("متن مخفی") }, modifier = Modifier.fillMaxWidth().height(100.dp),
                    colors = fColors(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text("رمز (اختیاری برای رمزنگاری)") }, modifier = Modifier.fillMaxWidth(),
                    colors = fColors(), shape = RoundedCornerShape(12.dp),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Button(onClick = {
                    try {
                        val payload = if (password.isNotBlank())
                            CryptoEngine.encryptString(text, password) else text
                        qrBmp = QrHelper.generate(payload)
                        val dir = File(context.filesDir, "qr").apply { mkdirs() }
                        val f = File(dir, "qr_${System.currentTimeMillis()}.png")
                        outShareFile = f
                        f.outputStream().use { qrBmp!!.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        SecurityUtils.saveStat(context, "qr_made")
                        Toast.makeText(context, "بارکد ساختهشد شد", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)) {
                    Text("ساخت بارکد", fontWeight = FontWeight.Bold)
                }
                qrBmp?.let {
                    Image(bitmap = it.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.size(240.dp).padding(8.dp))
                    Button(onClick = {
                        val f = outShareFile
                        if (f != null) com.ghostmago.app.util.ShareHelper.shareFile(context, f, "image/png")
                        else android.widget.Toast.makeText(context, "No QR file", android.widget.Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(12.dp)) { Text("اشتراک QR") }
                }
            } else {
                OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text("رمز (اگر QR رمزنگاری شده)") }, modifier = Modifier.fillMaxWidth(),
                    colors = fColors(), shape = RoundedCornerShape(12.dp),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Button(onClick = { picker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Secondary),
                    shape = RoundedCornerShape(12.dp)) {
                    Text("انتخاب تصویر QR", fontWeight = FontWeight.Bold)
                }
                if (result.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = CardDark),
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(result, color = TextPrimary, modifier = Modifier.padding(16.dp))
                    }
                }
            }
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
