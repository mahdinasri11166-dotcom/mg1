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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostmago.app.stego.ImageStego
import com.ghostmago.app.ui.theme.*
import com.ghostmago.app.util.SecurityUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageStegoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var outShareFile by remember { mutableStateOf<java.io.File?>(null) }
    var mode by remember { mutableStateOf("hide") }
    var secret by remember { mutableStateOf("") }
    var outFile by remember { mutableStateOf<java.io.File?>(null) }
    var password by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            try {
                context.contentResolver.openInputStream(uri)?.use {
                    preview = BitmapFactory.decodeStream(it)
                }
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پنهانسازی در تصویر", color = TextPrimary) },
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
            Modifier.fillMaxSize().padding(padding).imePadding().padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "hide", onClick = { mode = "hide" }, label = { Text("پنهان کردن") })
                FilterChip(selected = mode == "extract", onClick = { mode = "extract" }, label = { Text("استخراج") })
            }

            Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)) {
                Text("انتخاب تصویر")
            }

            preview?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(180.dp))
            }

            if (mode == "hide") {
                OutlinedTextField(value = secret, onValueChange = { secret = it },
                    label = { Text("متن مخفی") }, modifier = Modifier.fillMaxWidth().height(100.dp),
                    colors = fColors(), shape = RoundedCornerShape(12.dp))
            }

            OutlinedTextField(value = password, onValueChange = { password = it },
                label = { Text("رمز") }, modifier = Modifier.fillMaxWidth(),
                colors = fColors(), shape = RoundedCornerShape(12.dp),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())

            Button(
                onClick = {
                    val bmp = preview ?: return@Button
                    try {
                        if (mode == "hide") {
                            val out = ImageStego.hide(bmp, secret, password)
                            val dir = File(context.filesDir, "stego").apply { mkdirs() }
                            val f = File(dir, "stego_${System.currentTimeMillis()}.png")
                            outShareFile = f
                            ImageStego.savePng(out, f)
                            preview = out
                            resultText = "ذخیره شد: ${f.name}"
                            SecurityUtils.saveStat(context, "image_sent")
                            Toast.makeText(context, "تصویر پنهاننگاری ساخته شد", Toast.LENGTH_SHORT).show()
                        } else {
                            resultText = ImageStego.extract(bmp, password)
                            SecurityUtils.saveStat(context, "image_recv")
                        }
                    } catch (e: Exception) {
                        resultText = "خطا: ${e.message}"
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Secondary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (mode == "hide") "پنهان کردن در تصویر" else "استخراج از تصویر", fontWeight = FontWeight.Bold)
            }

            if (resultText.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(resultText, color = TextPrimary, modifier = Modifier.padding(16.dp), fontSize = 14.sp)
                }
            }
            Button(
                onClick = {
                    val f = outShareFile
                    if (f != null && f.exists()) {
                        com.ghostmago.app.util.ShareHelper.shareFile(context, f, "image/png")
                    } else {
                        android.widget.Toast.makeText(context, "No file yet", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(12.dp)
            ) { Text("اشتراک تصویر") }
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
