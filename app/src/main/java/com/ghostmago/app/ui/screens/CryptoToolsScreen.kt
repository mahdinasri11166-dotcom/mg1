package com.ghostmago.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.ghostmago.app.crypto.CryptoEngine
import com.ghostmago.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoToolsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var plain by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("encrypt") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ابزار رمزنگاری", color = TextPrimary) },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "encrypt", onClick = { mode = "encrypt" }, label = { Text("رمزنگاری") })
                FilterChip(selected = mode == "decrypt", onClick = { mode = "decrypt" }, label = { Text("رمزگشایی") })
            }

            Text(if (mode == "encrypt") "متن اصلی" else "متن رمزشده (Base64)", color = TextSecondary, fontSize = 13.sp)
            OutlinedTextField(
                value = plain,
                onValueChange = { plain = it },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                colors = fieldColors(),
                shape = RoundedCornerShape(12.dp)
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

            Button(
                onClick = {
                    try {
                        result = if (mode == "encrypt") {
                            CryptoEngine.encryptString(plain, password)
                        } else {
                            CryptoEngine.decryptString(plain, password)
                        }
                        Toast.makeText(context, "موفق", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        result = "خطا: ${e.message}"
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (mode == "encrypt") "رمزنگاری AES-256-GCM" else "رمزگشایی", fontWeight = FontWeight.Bold)
            }

            if (result.isNotEmpty()) {
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
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("crypto", result))
                        Toast.makeText(context, "کپی شد", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Secondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("کپی نتیجه")
                }
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
