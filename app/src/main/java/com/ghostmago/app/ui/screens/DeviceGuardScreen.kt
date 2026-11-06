package com.ghostmago.app.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostmago.app.data.AppState
import com.ghostmago.app.ui.theme.Accent
import com.ghostmago.app.ui.theme.BgDark
import com.ghostmago.app.ui.theme.CardDark
import com.ghostmago.app.ui.theme.Danger
import com.ghostmago.app.ui.theme.Primary
import com.ghostmago.app.ui.theme.SurfaceDark
import com.ghostmago.app.ui.theme.TextPrimary
import com.ghostmago.app.ui.theme.TextSecondary
import com.ghostmago.app.ui.theme.Warning
import com.ghostmago.app.util.Attestation
import com.ghostmago.app.util.DeviceGuard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceGuardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var orgOn by remember { mutableStateOf(AppState.orgDeviceGuard) }
    var wlOn by remember { mutableStateOf(AppState.enforceDeviceWhitelist) }
    var kiosk by remember { mutableStateOf(AppState.kioskMode) }
    var strict by remember { mutableStateOf(AppState.strictAttestation) }
    var reaction by remember { mutableStateOf(AppState.deviceGuardReaction) }
    var wlText by remember {
        mutableStateOf(DeviceGuard.whitelistEntries(context).joinToString("\n"))
    }
    var posture by remember { mutableStateOf(DeviceGuard.scanPosture(context)) }
    val integrity = remember { Attestation.localIntegrityHint(context) }

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_LONG).show()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("وضعیت دستگاه و ضددستکاری", color = TextPrimary, fontWeight = FontWeight.Bold) },
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
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("اثر انگشت این دستگاه", color = Primary, fontWeight = FontWeight.Bold)
                    Text(DeviceGuard.deviceFingerprint(), color = TextSecondary, fontSize = 12.sp)
                    Text("Integrity: $integrity", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        "وضعیت: ${posture.detail} · واکنش: ${posture.reaction}",
                        color = if (posture.issues.isEmpty()) Accent else Warning,
                        fontSize = 13.sp
                    )
                    Button(onClick = {
                        posture = DeviceGuard.scanPosture(context)
                        DeviceGuard.applyReaction(context, posture, null)
                        toast("اسکن شد")
                    }, modifier = Modifier.fillMaxWidth()) { Text("اسکن مجدد") }
                }
            }

            Card(colors = CardDefaults.cardColors(CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سیاست سازمانی", color = Primary, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("فعال‌سازی گارد دستگاه", color = TextPrimary)
                        Switch(checked = orgOn, onCheckedChange = {
                            orgOn = it; AppState.orgDeviceGuard = it; AppState.save(context)
                        })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Attestation سخت", color = TextPrimary)
                        Switch(checked = strict, onCheckedChange = {
                            strict = it; AppState.strictAttestation = it; AppState.save(context)
                        })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("فهرست سفید دستگاه", color = TextPrimary)
                            Text("فقط مدل/اثرانگشت مجاز", color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(checked = wlOn, onCheckedChange = {
                            wlOn = it; AppState.enforceDeviceWhitelist = it; AppState.save(context)
                        })
                    }
                    Text("واکنش هنگام نفوذ/عدم تأیید", color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("warn" to "هشدار", "readonly" to "فقط‌خواندنی", "disconnect" to "قطع مش").forEach { (id, label) ->
                            FilterChip(
                                selected = reaction == id,
                                onClick = {
                                    reaction = id
                                    AppState.deviceGuardReaction = id
                                    AppState.save(context)
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("حالت کیوسک عملیاتی", color = TextPrimary)
                            Text("فقط مش + پنهان‌نگاری + تنظیمات پایه", color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(checked = kiosk, onCheckedChange = {
                            kiosk = it; AppState.kioskMode = it; AppState.save(context)
                        })
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("فهرست سفید (هر خط یک مدل یا اثرانگشت)", color = Primary, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = wlText,
                        onValueChange = { wlText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("whitelist") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                DeviceGuard.addCurrentDeviceToWhitelist(context)
                                wlText = DeviceGuard.whitelistEntries(context).joinToString("\n")
                                toast("این دستگاه اضافه شد")
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("افزودن همین گوشی") }
                        Button(
                            onClick = {
                                DeviceGuard.setWhitelist(context, wlText.lines())
                                toast("ذخیره شد")
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("ذخیره لیست") }
                    }
                    Text(
                        "مدل: ${Build.MODEL} · API ${Build.VERSION.SDK_INT}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("پاکسازی از راه دور (فرمانده)", color = Danger, fontWeight = FontWeight.Bold)
                    Text(
                        "پیام کنترلی روی مش به همه یا هدف — گیرنده panic wipe محلی اجرا می‌کند.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        "از صفحه شبکه ارتباطی به‌عنوان فرمانده استفاده کنید؛ یا از API مش sendRemoteWipe.",
                        color = Warning,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
