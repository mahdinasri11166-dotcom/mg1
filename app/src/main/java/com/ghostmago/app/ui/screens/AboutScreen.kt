package com.ghostmago.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostmago.app.BuildConfig
import com.ghostmago.app.ui.theme.Accent
import com.ghostmago.app.ui.theme.BgDark
import com.ghostmago.app.ui.theme.CardDark
import com.ghostmago.app.ui.theme.Primary
import com.ghostmago.app.ui.theme.SurfaceDark
import com.ghostmago.app.ui.theme.TextPrimary
import com.ghostmago.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("درباره مشکات", color = TextPrimary, fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("مشکات (Mishkat)", color = Primary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text("سازنده: Mr Elf", color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                "نسخه ${BuildConfig.VERSION_NAME}  ·  کد ${BuildConfig.VERSION_CODE}",
                color = TextSecondary,
                fontSize = 13.sp
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("معنای نام", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        "نام برنامه برگرفته از آیه ۳۵ سوره نور است: «كَمِشْكَاةٍ فِيهَا مِصْبَاحٌ». " +
                            "مشکات یعنی چراغدان یا محفظه‌ای که نور در آن پنهان و محفوظ است؛ " +
                            "ترکیب «نور» و «پنهان/محفوظ».",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("قابلیت‌های اصلی", color = TextPrimary, fontWeight = FontWeight.Bold)
                    val items = listOf(
                        "شبکه ارتباطی آفلاین (وای‌فای، بلوتوث، رادیو USB)",
                        "سلسله‌مراتب فرمانده کل / فرمانده / سرگروه / نیرو",
                        "چت گروهی و خصوصی، ویس و فایل",
                        "پنهان‌نگاری متن، تصویر، صوت و پیامک",
                        "ورود دو لایه (ماشین‌حساب + رمز)",
                        "حالت طعمه، Panic، ممیزی فرمانده",
                        "رمزنگاری AES-256-GCM و Argon2id"
                    )
                    items.forEach {
                        Text("• $it", color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
            }

            Text(
                "آیکون لانچر برای پوشش امنیتی «ماشین حساب» است؛ نام واقعی محصول مشکات است.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}
