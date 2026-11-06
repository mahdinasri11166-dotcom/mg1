package com.ghostmago.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostmago.app.data.AppState
import com.ghostmago.app.data.MeshRole
import com.ghostmago.app.ui.theme.*

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var role by remember { mutableStateOf(MeshRole.SOLDIER) }
    var roleSecret by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("خوش آمدید", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("مشکات — راهنمای سریع", color = Primary, fontSize = 14.sp)

        when (step) {
            0 -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("۱) بدون اینترنت روی یک Wi‑Fi مشترک کار میکند.", color = TextSecondary, fontSize = 14.sp, lineHeight = 22.sp)
                        Text("۲) فرمانده بارکد دعوت میسازد؛ نیروها اسکن میکنند.", color = TextSecondary, fontSize = 14.sp, lineHeight = 22.sp)
                        Text("۳) چت گروهی و خصوصی در شبکه ارتباطی.", color = TextSecondary, fontSize = 14.sp, lineHeight = 22.sp)
                        Text("۴) پین ماشینحساب و رمز ورود را عوض کنید.", color = TextSecondary, fontSize = 14.sp, lineHeight = 22.sp)
                    }
                }
                Button(
                    onClick = { step = 1 },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("ادامه — انتخاب نقش", fontWeight = FontWeight.Bold) }
            }
            1 -> {
                Text("نقش شما چیست؟", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "برای فرمانده کل و فرمانده، عبارت عبور نقش لازم است.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                MeshRole.entries.filter { it != MeshRole.RECRUIT }.forEach { r ->
                    FilterChip(
                        selected = role == r,
                        onClick = {
                            role = r
                            roleSecret = ""
                        },
                        label = { Text("${r.symbol} ${r.fa}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (role.requiresRolePassword()) {
                    OutlinedTextField(
                        value = roleSecret,
                        onValueChange = { roleSecret = it },
                        label = { Text("عبارت عبور نقش") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = fieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text("بدون عبارت عبور صحیح، این نقش فعال نمیشود.", color = Warning, fontSize = 12.sp)
                } else {
                    Text("این نقش نیاز به عبارت عبور ندارد.", color = TextMuted, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        if (role.requiresRolePassword() && !role.checkRolePassword(roleSecret)) {
                            Toast.makeText(context, "عبارت عبور نقش نادرست است", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        AppState.meshRole = role
                        AppState.save(context)
                        roleSecret = ""
                        Toast.makeText(context, "نقش ${role.fa} ثبت شد", Toast.LENGTH_SHORT).show()
                        step = 2
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("ادامه — امنیت", fontWeight = FontWeight.Bold) }
            }
            else -> {
                Text("تغییر پین و رمز (توصیه میشود)", color = TextPrimary, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 12) newPin = it },
                    label = { Text("پین جدید ماشینحساب (خالی = همان 0000)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = newPw,
                    onValueChange = { newPw = it },
                    label = { Text("رمز ورود جدید (خالی = پیشفرض)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation()
                )
                Button(
                    onClick = {
                        if (newPin.length >= 4) AppState.setVaultPin(context, newPin)
                        if (newPw.length >= 4) AppState.setPassword(context, newPw)
                        AppState.onboardingDone = true
                        AppState.save(context)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Secondary),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("ورود به برنامه", fontWeight = FontWeight.Bold) }
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
    unfocusedContainerColor = CardDark,
    focusedLabelColor = Primary,
    unfocusedLabelColor = TextSecondary
)
