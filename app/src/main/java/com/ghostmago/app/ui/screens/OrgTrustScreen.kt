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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostmago.app.data.AppState
import com.ghostmago.app.orgtrust.CrlStore
import com.ghostmago.app.orgtrust.IdentityPolicy
import com.ghostmago.app.orgtrust.OrgTrust
import com.ghostmago.app.ui.theme.Accent
import com.ghostmago.app.ui.theme.BgDark
import com.ghostmago.app.ui.theme.CardDark
import com.ghostmago.app.ui.theme.Primary
import com.ghostmago.app.ui.theme.Secondary
import com.ghostmago.app.ui.theme.SurfaceDark
import com.ghostmago.app.ui.theme.TextPrimary
import com.ghostmago.app.ui.theme.TextSecondary
import com.ghostmago.app.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrgTrustScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var caInfo by remember {
        mutableStateOf(OrgTrust.getCaPublic(context)?.let { "CA: ${it.caId}" + if (it.hasPrivate) " (کلید خصوصی هست)" else " (فقط عمومی)" } ?: "CA ساخته نشده")
    }
    var alias by remember { mutableStateOf(AppState.nickname) }
    var role by remember { mutableStateOf("soldier") }
    var inviteTtl by remember { mutableIntStateOf(24) }
    var inviteOut by remember { mutableStateOf("") }
    var caBundle by remember { mutableStateOf("") }
    var crlText by remember { mutableStateOf(CrlStore.exportJson(context)) }
    var revokeSerial by remember { mutableStateOf("") }
    var myCert by remember {
        mutableStateOf(OrgTrust.getMyCertificate(context)?.let { "${it.alias} · ${it.serial}" } ?: "گواهی محلی نیست")
    }

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_LONG).show()
    fun clip(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("هویت و اعتماد سازمانی", color = TextPrimary, fontWeight = FontWeight.Bold) },
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
            Text(
                "چهار لایه: گواهی CA · دعوت امضاشده · CRL آفلاین · نام مستعار عملیاتی",
                color = TextSecondary,
                fontSize = 13.sp
            )

            Card(colors = CardDefaults.cardColors(CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("۱) مرجع گواهی (CA)", color = Primary, fontWeight = FontWeight.Bold)
                    Text(caInfo, color = TextSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                try {
                                    val ca = OrgTrust.initCa(context)
                                    caInfo = "CA: ${ca.caId} (کلید خصوصی هست)"
                                    caBundle = OrgTrust.exportCaPublicBundle(context)
                                    toast("CA سازمانی ساخته شد")
                                } catch (e: Exception) {
                                    toast(e.message ?: "خطا")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(Primary)
                        ) { Text("ایجاد CA فرمانده") }
                        Button(
                            onClick = {
                                try {
                                    caBundle = OrgTrust.exportCaPublicBundle(context)
                                    clip("ca", caBundle)
                                    toast("باندل عمومی CA کپی شد")
                                } catch (e: Exception) {
                                    toast(e.message ?: "خطا")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(Secondary)
                        ) { Text("کپی کلید عمومی") }
                    }
                    OutlinedTextField(
                        value = caBundle,
                        onValueChange = { caBundle = it },
                        label = { Text("باندل CA (وارد کردن روی گوشی نیرو)") },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 100.dp)
                    )
                    Button(
                        onClick = {
                            try {
                                OrgTrust.importCaPublicBundle(context, caBundle.trim())
                                caInfo = OrgTrust.getCaPublic(context)?.let { "CA: ${it.caId}" } ?: caInfo
                                toast("کلید عمومی CA وارد شد")
                            } catch (e: Exception) {
                                toast(e.message ?: "خطا")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("وارد کردن CA عمومی") }
                }
            }

            Card(colors = CardDefaults.cardColors(CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("۲) گواهی عضو + نام مستعار عملیاتی", color = Primary, fontWeight = FontWeight.Bold)
                    Text("شماره تلفن و ایمیل در نام مجاز نیست و حذف می‌شود.", color = Warning, fontSize = 12.sp)
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        label = { Text("نام مستعار عملیاتی") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("soldier", "leader", "commander").forEach { r ->
                            FilterChip(
                                selected = role == r,
                                onClick = { role = r },
                                label = { Text(r, fontSize = 11.sp) }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            try {
                                val clean = IdentityPolicy.sanitizeAlias(alias)
                                AppState.nickname = clean
                                AppState.save(context)
                                alias = clean
                                val cert = OrgTrust.issueCertificate(context, clean, role, validDays = 30)
                                myCert = "${cert.alias} · ${cert.serial}"
                                clip("cert", cert.toJson().toString())
                                toast("گواهی صادر و کپی شد: ${cert.serial}")
                            } catch (e: Exception) {
                                toast(e.message ?: "خطا — فقط فرمانده با CA")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(Primary)
                    ) { Text("صدور گواهی برای این دستگاه") }
                    Text("گواهی من: $myCert", color = TextSecondary, fontSize = 12.sp)
                    Button(
                        onClick = {
                            IdentityPolicy.enforceOperationalNickname(context)
                            alias = AppState.nickname
                            toast("نام عملیاتی اعمال شد: $alias")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("اعمال سیاست نام مستعار") }
                }
            }

            Card(colors = CardDefaults.cardColors(CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("۳) دعوت امضاشده یک‌بارمصرف (QR)", color = Primary, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 6, 24, 72).forEach { h ->
                            FilterChip(
                                selected = inviteTtl == h,
                                onClick = { inviteTtl = h },
                                label = { Text("${h}س", fontSize = 11.sp) }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            try {
                                val inv = OrgTrust.issueInvite(
                                    context,
                                    groupId = AppState.groupId.ifBlank { AppState.lastGroupId.ifBlank { "default" } },
                                    role = role,
                                    ttlHours = inviteTtl,
                                    maxUses = 1
                                )
                                inviteOut = inv.toQrPayload()
                                // also keep classic mesh password invite path compatible via pending
                                OrgTrust.savePendingInvite(
                                    context,
                                    org.json.JSONObject()
                                        .put("tid", inv.tokenId)
                                        .put("gid", inv.groupId)
                                        .put("role", inv.role)
                                        .put("exp", inv.expiresAt)
                                        .put("uses", inv.maxUses)
                                        .put("caId", inv.caId)
                                        .put("sig", inv.signatureB64)
                                        .toString()
                                )
                                clip("invite", inviteOut)
                                toast("توکن دعوت ساخته و کپی شد")
                            } catch (e: Exception) {
                                toast(e.message ?: "خطا")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(Accent)
                    ) { Text("ساخت توکن دعوت امضاشده") }
                    OutlinedTextField(
                        value = inviteOut,
                        onValueChange = { inviteOut = it },
                        label = { Text("توکن دعوت (برای QR یا جای‌گذاری)") },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp)
                    )
                    Button(
                        onClick = {
                            try {
                                val tok = OrgTrust.InviteToken.parseQr(inviteOut)
                                    ?: throw IllegalArgumentException("فرمت دعوت نامعتبر")
                                val err = OrgTrust.verifyInvite(context, tok)
                                if (err != null) throw IllegalArgumentException(err)
                                OrgTrust.savePendingInvite(
                                    context,
                                    org.json.JSONObject()
                                        .put("tid", tok.tokenId)
                                        .put("gid", tok.groupId)
                                        .put("role", tok.role)
                                        .put("exp", tok.expiresAt)
                                        .put("uses", tok.maxUses)
                                        .put("caId", tok.caId)
                                        .put("sig", tok.signatureB64)
                                        .toString()
                                )
                                toast("دعوت ذخیره شد — در join ارسال می‌شود")
                            } catch (e: Exception) {
                                toast(e.message ?: "خطا")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ذخیره دعوت برای پیوستن") }
                }
            }

            Card(colors = CardDefaults.cardColors(CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("۴) فهرست ابطال (CRL) آفلاین", color = Primary, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = revokeSerial,
                        onValueChange = { revokeSerial = it },
                        label = { Text("سریال گواهی برای ابطال") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (revokeSerial.isBlank()) {
                                toast("سریال خالی است")
                                return@Button
                            }
                            CrlStore.revoke(context, revokeSerial.trim(), "manual")
                            crlText = CrlStore.exportJson(context)
                            toast("به CRL اضافه شد")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Warning),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ابطال سریال") }
                    OutlinedTextField(
                        value = crlText,
                        onValueChange = { crlText = it },
                        label = { Text("خروجی / ورودی CRL") },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                crlText = CrlStore.exportJson(context)
                                clip("crl", crlText)
                                toast("CRL کپی شد")
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("خروجی CRL") }
                        Button(
                            onClick = {
                                try {
                                    CrlStore.importJson(context, crlText)
                                    crlText = CrlStore.exportJson(context)
                                    toast("CRL ادغام شد")
                                } catch (e: Exception) {
                                    toast(e.message ?: "خطا")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("ورود CRL") }
                    }
                    Text(
                        "ابطال‌شده‌ها: ${CrlStore.listSerials(context).joinToString().ifBlank { "—" }}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
