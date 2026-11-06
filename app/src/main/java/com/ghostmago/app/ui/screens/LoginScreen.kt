package com.ghostmago.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostmago.app.data.AppState
import com.ghostmago.app.data.AuditLog
import com.ghostmago.app.ui.theme.*

@Composable
fun LoginScreen(onSuccess: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var attempts by remember { mutableIntStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(GradientStart, BgDark, GradientEnd)
                )
            )
    ) {
        // soft glow blobs
        Box(
            Modifier
                .size(220.dp)
                .offset((-40).dp, 80.dp)
                .clip(RoundedCornerShape(110.dp))
                .background(Primary.copy(alpha = 0.12f))
        )
        Box(
            Modifier
                .size(180.dp)
                .align(Alignment.BottomEnd)
                .offset(40.dp, (-60).dp)
                .clip(RoundedCornerShape(90.dp))
                .background(Accent.copy(alpha = 0.10f))
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(28.dp)
                .imePadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(listOf(Primary.copy(0.35f), Accent.copy(0.25f)))
                    )
                    .border(1.dp, Primary.copy(0.5f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Shield, null, tint = TextPrimary, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text("مشکات", color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Mishkat", color = TextSecondary, fontSize = 14.sp)
            Text("سامانه امن آفلاین", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text("سازنده Mr Elf", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(36.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.92f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("دسترسی به گاوصندوق", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = "" },
                        label = { Text("عبارت عبور") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = Primary) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Border,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Primary,
                            focusedContainerColor = CardElevated,
                            unfocusedContainerColor = CardElevated
                        )
                    )
                    if (error.isNotBlank()) {
                        Text(error, color = Danger, fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            when (val r = AppState.tryLogin(context, password)) {
                                "ok" -> {
                                    AppState.touchSession()
                                    AuditLog.append(context, "LOGIN", "ok")
                                    onSuccess()
                                }
                                "locked" -> error = "تلاش زیاد — کمی صبر کنید"
                                else -> {
                                    attempts++
                                    error = "عبارت عبور نادرست ($attempts)"
                                    Toast.makeText(context, "دسترسی رد شد", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("باز کردن", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("رمز سرتاسر · مش آفلاین · بدون ابر", color = TextMuted, fontSize = 12.sp)
        }
    }
}
