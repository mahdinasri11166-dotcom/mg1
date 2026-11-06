package com.ghostmago.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ghostmago.app.data.AppState
import com.ghostmago.app.util.DeviceGuard
import com.ghostmago.app.ui.Screen
import com.ghostmago.app.ui.theme.*

data class MenuItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        AppState.touchSession()
        while (true) {
            kotlinx.coroutines.delay(15_000)
            if (AppState.shouldAutoLock()) {
                AppState.lockSession()
                nav.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
                break
            }
            AppState.touchSession()
            try {
                com.ghostmago.app.data.DeadManSwitch.checkIn(context)
                val fire = com.ghostmago.app.data.DeadManSwitch.poll(context)
                if (fire != null) {
                    android.widget.Toast.makeText(context, "DMS: $fire", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
            }
        }
    }

    if (AppState.isHoneyMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("پیامها", color = TextPrimary) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
                )
            },
            containerColor = BgDark
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
                AppState.honeyMessages().forEach { (n, m) ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(n, color = Primary, fontWeight = FontWeight.Bold)
                            Text(m, color = TextPrimary)
                        }
                    }
                }
            }
        }
        return
    }

    val items = listOf(
        MenuItem("داشبورد فرمانده", "وضعیت · اتاق · ممیزی", Icons.Default.Home, Screen.CommanderDash.route, Primary),
        MenuItem("شبکه ارتباطی", "گروه و خصوصی و ویس", Icons.Default.Share, Screen.Mesh.route, Accent),
        MenuItem("پنهاننگاری پیامک", "مخاطب و جستجوی مخفی", Icons.Default.Sms, Screen.SmsStego.route, Secondary),
        MenuItem("پنهاننگاری متن", "پنهان در نوشته", Icons.Default.VisibilityOff, Screen.SendStego.route, Secondary),
        MenuItem("خواندن پنهاننگاری", "استخراج پیام", Icons.Default.Visibility, Screen.ReadStego.route, Secondary),
        MenuItem("پنهاننگاری عکس", "پنهان در تصویر", Icons.Default.Image, Screen.ImageStego.route, Warning),
        MenuItem("پنهاننگاری صوت", "فایل WAV", Icons.Default.Star, Screen.AudioStego.route, Warning),
        MenuItem("پنهان فایل در تصویر", "PDF/ZIP داخل PNG", Icons.Default.InsertDriveFile, Screen.FileStego.route, Warning),
        MenuItem("بارکد", "ساخت و رمز", Icons.Default.QrCode, Screen.Qr.route, Primary),
        MenuItem("اسکن بارکد", "دوربین زنده", Icons.Default.QrCodeScanner, Screen.QrScan.route, Primary),
        MenuItem("وایفای مستقیم", "بدون مودم", Icons.Default.Settings, Screen.WifiDirect.route, Accent),
        MenuItem("رمزنگاری", "ابزار AES", Icons.Default.Lock, Screen.CryptoTools.route, Secondary),
        MenuItem("امنیت دفاعی", "نفوذ · پاکسازی · خزانه", Icons.Default.Shield, Screen.Defense.route, Danger),
        MenuItem("هویت و اعتماد", "CA · دعوت · CRL · نام مستعار", Icons.Default.Person, Screen.OrgTrust.route, Primary),
        MenuItem("ضددستکاری", "whitelist · attestation · کیوسک", Icons.Default.Security, Screen.DeviceGuard.route, Danger),
        MenuItem("ممیزی و انطباق", "SOP · حادثه · دو نفره · نگهداری", Icons.Default.Info, Screen.Compliance.route, Primary),
        MenuItem("پیشرفته", "شیمیر و وحشت و کلید مرگ", Icons.Default.Security, Screen.Advanced.route, Danger),
        MenuItem("تنظیمات", "نقش و امنیت", Icons.Default.Settings, Screen.Settings.route, TextSecondary),
        MenuItem("راهنما", "نحوه استفاده", Icons.Default.Info, Screen.About.route, TextMuted)
    )
    val visibleItems = items.filter { !DeviceGuard.isKioskBlocked(it.route) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("مشکات", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            "${AppState.meshRole.symbol} ${AppState.meshRole.fa}  ·  ${AppState.nickname}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        AppState.lockSession()
                        nav.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }) {
                        Icon(Icons.Default.Lock, null, tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = BgDark
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Primary.copy(0.40f), Accent.copy(0.22f), CardDark)
                        )
                    )
                    .border(1.dp, Primary.copy(0.30f), RoundedCornerShape(22.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text("داشبورد عملیاتی", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("شبکه ارتباطی آفلاین · پنهان‌نگاری · سلسله‌مراتب", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {},
                            label = { Text("رمز سرتاسر", fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = ChipBg)
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text("آفلاین اول", fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = ChipBg)
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text("رادیو USB", fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = ChipBg)
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text("سازنده Mr Elf", fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = ChipBg)
                        )
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(visibleItems) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(124.dp)
                            .clickable {
                                AppState.touchSession()
                                nav.navigate(item.route)
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(item.color.copy(0.28f), item.color.copy(0.08f))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(item.icon, null, tint = item.color, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text(item.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                                Text(item.subtitle, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
