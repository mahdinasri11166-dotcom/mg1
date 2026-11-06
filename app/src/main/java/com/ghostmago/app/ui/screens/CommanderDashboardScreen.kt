package com.ghostmago.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ghostmago.app.data.AppState
import com.ghostmago.app.data.AuditLog
import com.ghostmago.app.data.OpProfile
import com.ghostmago.app.data.RoomStore
import com.ghostmago.app.ui.Screen
import com.ghostmago.app.ui.theme.Accent
import com.ghostmago.app.ui.theme.BgDark
import com.ghostmago.app.ui.theme.Border
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommanderDashboardScreen(onBack: () -> Unit, nav: NavController? = null) {
    val context = LocalContext.current
    val isCommander = AppState.meshRole.canApprove()

    var roomName by remember { mutableStateOf("") }
    var roomPass by remember { mutableStateOf("") }
    var rooms by remember { mutableStateOf(RoomStore.list()) }
    var audit by remember { mutableStateOf(AuditLog.readAll(context).take(40)) }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.US) }

    // Sync active mesh group into room list so created groups appear here
    LaunchedEffect(Unit) {
        try {
            val gid = AppState.groupId
            val pw = AppState.lastGroupPassword.ifBlank { AppState.pendingMeshPassword }
            if (gid.isNotBlank() && pw.length >= 4) {
                RoomStore.ensureFromActiveMesh(context, gid, pw, "گروه فعال")
            }
        } catch (_: Exception) {
        }
        rooms = RoomStore.list()
        audit = AuditLog.readAll(context).take(40)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("داشبورد فرمانده", color = TextPrimary, fontWeight = FontWeight.Bold)
                },
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
            Text(
                "نقش فعلی: ${AppState.meshRole.fa}",
                color = Secondary,
                fontWeight = FontWeight.SemiBold
            )

            Text("حالت عملیاتی", color = TextPrimary, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "شهری" to OpProfile.URBAN,
                    "میدانی" to OpProfile.FIELD,
                    "سکوت رادیویی" to OpProfile.SILENT,
                    "آماده‌باش" to OpProfile.STANDBY
                ).forEach { (label, prof) ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            try {
                                prof.applyTo(AppState)
                            } catch (_: Exception) {
                            }
                            AppState.save(context)
                            try {
                                AuditLog.append(context, "OP_PROFILE", label)
                            } catch (_: Exception) {
                            }
                            Toast.makeText(context, "حالت: $label", Toast.LENGTH_SHORT).show()
                        },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            if (isCommander) {
                Text("اتاق‌ها / نشست‌های گروهی", color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    "گروهی که در شبکه ارتباطی می‌سازید اینجا ثبت می‌شود. می‌توانید فعال/غیرفعال کنید یا برای اتصال انتخاب کنید.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("نام اتاق جدید") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = dashColors(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = roomPass,
                    onValueChange = { roomPass = it },
                    label = { Text("رمز اتاق (حداقل ۴ کاراکتر)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = dashColors(),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        if (roomPass.length < 4) {
                            Toast.makeText(context, "رمز حداقل ۴ کاراکتر", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        try {
                            val room = RoomStore.add(context, roomName.ifBlank { "اتاق جدید" }, roomPass)
                            AppState.lastGroupPassword = room.password
                            AppState.pendingMeshPassword = room.password
                            rooms = RoomStore.list()
                            roomName = ""
                            roomPass = ""
                            audit = AuditLog.readAll(context).take(40)
                            Toast.makeText(
                                context,
                                "اتاق «${room.name}» ساخته شد — شناسه: ${room.id}",
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "خطا", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("ساخت اتاق / نشست", fontWeight = FontWeight.Bold) }

                if (rooms.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "هنوز اتاقی نیست. از اینجا بسازید یا در «شبکه ارتباطی» یک گروه میزبان بسازید تا اینجا ظاهر شود.",
                            color = TextSecondary,
                            modifier = Modifier.padding(14.dp),
                            fontSize = 13.sp
                        )
                    }
                }

                rooms.forEach { r ->
                    val isActive = AppState.groupId == r.id
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (r.enabled) CardDark else CardDark.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(r.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "شناسه: ${r.id}" +
                                    if (isActive) " · انتخاب‌شده" else "" +
                                    " · " + if (r.enabled) "فعال" else "غیرفعال",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        RoomStore.setEnabled(context, r.id, !r.enabled)
                                        rooms = RoomStore.list()
                                        audit = AuditLog.readAll(context).take(40)
                                        Toast.makeText(
                                            context,
                                            if (!r.enabled) "اتاق فعال شد" else "اتاق غیرفعال شد",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (r.enabled) Danger else Secondary
                                    )
                                ) {
                                    Text(
                                        if (r.enabled) "غیرفعال کردن" else "فعال کردن",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (!r.enabled) {
                                            Toast.makeText(context, "اول اتاق را فعال کنید", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        if (RoomStore.activate(context, r)) {
                                            Toast.makeText(
                                                context,
                                                "اتاق «${r.name}» آماده اتصال است",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            try {
                                                nav?.navigate(Screen.Mesh.route)
                                            } catch (_: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    "به شبکه ارتباطی بروید و اتصال را بزنید",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                                ) {
                                    Text("ورود به گروه", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                            TextButton(
                                onClick = {
                                    RoomStore.remove(context, r.id)
                                    rooms = RoomStore.list()
                                    Toast.makeText(context, "حذف شد", Toast.LENGTH_SHORT).show()
                                }
                            ) { Text("حذف اتاق", color = Danger, fontSize = 12.sp) }
                        }
                    }
                }

                Text("ممیزی محلی (فقط فرمانده)", color = TextPrimary, fontWeight = FontWeight.Bold)
                if (audit.isEmpty()) {
                    Text("رویدادی ثبت نشده", color = TextSecondary, fontSize = 13.sp)
                } else {
                    audit.forEach { (ts, type, detail) ->
                        Text(
                            "${fmt.format(Date(ts))} · $type · $detail",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                Text(
                    "داشبورد کامل فقط برای فرمانده / فرمانده کل است.",
                    color = Warning,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun dashColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = Border,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Primary,
    focusedContainerColor = CardDark,
    unfocusedContainerColor = CardDark
)
