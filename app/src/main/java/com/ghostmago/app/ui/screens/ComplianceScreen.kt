package com.ghostmago.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.ghostmago.app.data.ImmutableAudit
import com.ghostmago.app.data.IncidentReport
import com.ghostmago.app.data.MessageClassification
import com.ghostmago.app.data.SopChecklist
import com.ghostmago.app.data.TwoPersonRule
import com.ghostmago.app.ui.theme.Accent
import com.ghostmago.app.ui.theme.BgDark
import com.ghostmago.app.ui.theme.CardDark
import com.ghostmago.app.ui.theme.Danger
import com.ghostmago.app.ui.theme.Primary
import com.ghostmago.app.ui.theme.Secondary
import com.ghostmago.app.ui.theme.SurfaceDark
import com.ghostmago.app.ui.theme.TextPrimary
import com.ghostmago.app.ui.theme.TextSecondary
import com.ghostmago.app.ui.theme.Warning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplianceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var retention by remember { mutableIntStateOf(AppState.messageRetentionHours) }
    var clazz by remember { mutableStateOf(AppState.defaultClassification) }
    var twoPerson by remember { mutableStateOf(AppState.requireTwoPersonOps) }
    var secondPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var incidentNote by remember { mutableStateOf("") }
    var auditPreview by remember {
        mutableStateOf(ImmutableAudit.readAll(context).take(12).joinToString("\n") {
            val t = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(it.optLong("t")))
            "$t ${it.optString("type")}: ${it.optString("detail")}"
        })
    }
    val needsDrill = remember { SopChecklist.needsDrill(context) }

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_LONG).show()
    fun clip(s: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("mishkat", s))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("عملیات · ممیزی · انطباق", color = TextPrimary, fontWeight = FontWeight.Bold) },
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
            if (needsDrill) {
                Card(colors = CardDefaults.cardColors(Warning.copy(alpha = 0.25f)), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "تمرین ماهانه Panic/Duress هنوز انجام نشده یا بیش از ۳۰ روز گذشته.",
                        color = Warning,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("۱) ممیزی زنجیره‌ای (حذف‌نشدنی)", color = Primary, fontWeight = FontWeight.Bold)
                    Text(
                        if (ImmutableAudit.isTampered(context)) "⚠ زنجیره ممیزی آسیب دیده"
                        else "زنجیره سالم",
                        color = if (ImmutableAudit.isTampered(context)) Danger else Accent,
                        fontSize = 12.sp
                    )
                    Text(auditPreview.ifBlank { "رویدادی نیست" }, color = TextSecondary, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            auditPreview = ImmutableAudit.readAll(context).take(12).joinToString("\n") {
                                val t = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(it.optLong("t")))
                                "$t ${it.optString("type")}: ${it.optString("detail")}"
                            }
                        }, modifier = Modifier.weight(1f)) { Text("بروزرسانی") }
                        Button(onClick = {
                            val j = ImmutableAudit.exportJson(context)
                            clip(j)
                            toast("خروجی ممیزی کپی شد")
                        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(Secondary)) {
                            Text("خروجی")
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("۲) سطح‌بندی پیام + ۳) نگهداری", color = Primary, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            MessageClassification.PUBLIC to "عمومی",
                            MessageClassification.SENSITIVE to "حساس",
                            MessageClassification.SECRET to "سری"
                        ).forEach { (id, label) ->
                            FilterChip(
                                selected = clazz == id,
                                onClick = {
                                    clazz = id
                                    AppState.defaultClassification = id
                                    AppState.save(context)
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                    Text("پیام سری فوروارد نمی‌شود · انقضای کوتاه‌تر", color = TextSecondary, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 24, 48, 168).forEach { h ->
                            FilterChip(
                                selected = retention == h,
                                onClick = {
                                    retention = h
                                    AppState.messageRetentionHours = h
                                    AppState.save(context)
                                },
                                label = { Text(if (h == 0) "بدون" else "${h}س", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("۴) گزارش حادثه (فقط متادیتا)", color = Primary, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = incidentNote,
                        onValueChange = { incidentNote = it },
                        label = { Text("یادداشت کوتاه (بدون محتوای پیام)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val report = IncidentReport.build(context, incidentNote)
                            clip(report)
                            ImmutableAudit.append(context, "INCIDENT_REPORT", incidentNote.take(40))
                            toast("گزارش بدون متن پیام کپی شد")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(Warning)
                    ) { Text("ساخت گزارش افسر امنیت") }
                }
            }

            Card(colors = CardDefaults.cardColors(CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("۵) قاعده دو نفره", color = Primary, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("اجبار تأیید نفر دوم", color = TextPrimary)
                        Switch(checked = twoPerson, onCheckedChange = {
                            twoPerson = it
                            AppState.requireTwoPersonOps = it
                            AppState.save(context)
                        })
                    }
                    OutlinedTextField(
                        value = secondPw,
                        onValueChange = { secondPw = it },
                        label = { Text("رمز نفر دوم (تنظیم)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        if (secondPw.length < 6) {
                            toast("حداقل ۶ کاراکتر")
                            return@Button
                        }
                        TwoPersonRule.setSecondOperatorHash(context, secondPw)
                        secondPw = ""
                        toast("نفر دوم ثبت شد")
                    }, modifier = Modifier.fillMaxWidth()) { Text("ثبت نفر دوم") }
                    OutlinedTextField(
                        value = confirmPw,
                        onValueChange = { confirmPw = it },
                        label = { Text("تأیید عملیات خطرناک — رمز نفر دوم") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        if (TwoPersonRule.confirmSecond(context, confirmPw)) toast("تأیید دو نفره OK")
                        else toast("تأیید ناموفق")
                        confirmPw = ""
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(Secondary)) {
                        Text("آزمایش تأیید دو نفره")
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("۶) SOP و تمرین ماهانه", color = Primary, fontWeight = FontWeight.Bold)
                    Text(SopChecklist.sopText(), color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                    Button(
                        onClick = {
                            SopChecklist.markDrillDone(context)
                            toast("تمرین ثبت شد")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(Accent)
                    ) { Text("ثبت تمرین Panic/Duress این ماه") }
                }
            }
        }
    }
}
