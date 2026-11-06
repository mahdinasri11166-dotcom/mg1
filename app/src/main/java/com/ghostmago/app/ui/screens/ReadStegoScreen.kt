package com.ghostmago.app.ui.screens

import android.widget.Toast
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
import com.ghostmago.app.stego.StegoEngine
import com.ghostmago.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadStegoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var stego by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("خواندن پیام مخفی", color = TextPrimary) },
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
            Text("پیام دریافتی را اینجا بچسبانید", color = TextSecondary, fontSize = 13.sp)
            OutlinedTextField(
                value = stego,
                onValueChange = { stego = it },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Primary,
                    focusedContainerColor = CardDark,
                    unfocusedContainerColor = CardDark
                ),
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("متن پیام را paste کنید…", color = TextSecondary) }
            )

            if (stego.isNotEmpty()) {
                val has = StegoEngine.hasHidden(stego)
                Text(
                    if (has) "✅ حاوی داده مخفی است" else "⚠️ داده مخفی پیدا نشد",
                    color = if (has) Secondary else Warning,
                    fontSize = 13.sp
                )
            }

            Text("رمز عبور", color = TextSecondary, fontSize = 13.sp)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Primary,
                    focusedContainerColor = CardDark,
                    unfocusedContainerColor = CardDark
                ),
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
                        if (stego.isBlank() || password.isBlank()) {
                            error = "متن و رمز را وارد کنید"
                            return@Button
                        }
                        result = StegoEngine.decode(stego, password)
                        com.ghostmago.app.util.SecurityUtils.saveStat(context, "received")
                        Toast.makeText(context, "رمزگشایی موفق", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        error = e.message ?: "رمز اشتباه یا داده خراب"
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Secondary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("استخراج پیام", fontWeight = FontWeight.Bold)
            }

            if (result.isNotEmpty()) {
                Text("پیام مخفی:", color = Secondary, fontSize = 13.sp)
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        result,
                        color = TextPrimary,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
