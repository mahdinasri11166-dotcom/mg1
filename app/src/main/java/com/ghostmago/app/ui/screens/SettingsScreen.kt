package com.ghostmago.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostmago.app.data.AppState
import com.ghostmago.app.data.MeshRole
import com.ghostmago.app.ui.theme.*
import com.ghostmago.app.util.SecurityUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var nick by remember { mutableStateOf(AppState.nickname) }
    var oldPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var duressPw by remember { mutableStateOf("") }
    var dark by remember { mutableStateOf(AppState.darkTheme) }
    var accent by remember { mutableIntStateOf(AppState.accentColor) }
    var role by remember { mutableStateOf(AppState.meshRole) }
    var roleGatePw by remember { mutableStateOf("") }
    var radioSilence by remember { mutableStateOf(AppState.radioSilence) }
    var ephSec by remember { mutableIntStateOf(AppState.ephemeralDefaultSec) }
    var vaultPin by remember { mutableStateOf(AppState.vaultPin) }
    var safeWord by remember { mutableStateOf(AppState.safeWord) }
    var rndDelay by remember { mutableStateOf(AppState.randomSendDelay) }
    var readOnly by remember { mutableStateOf(AppState.forceReadOnly) }
    var coverOn by remember { mutableStateOf(AppState.coverTraffic) }
    var coverSec by remember { mutableIntStateOf(AppState.coverIntervalSec) }
    var reqSafety by remember { mutableStateOf(AppState.requireSafetyNumber) }
    var sealedOn by remember { mutableStateOf(AppState.sealedSender) }
    var hwKs by remember { mutableStateOf(AppState.useHardwareKeystore) }
    var profile by remember { mutableStateOf(AppState.opProfileId) }
    var dmsOn by remember { mutableStateOf(com.ghostmago.app.data.DeadManSwitch.isEnabled(context)) }
    var dmsMin by remember { mutableIntStateOf(com.ghostmago.app.data.DeadManSwitch.intervalMin(context)) }
    var dmsMsg by remember { mutableStateOf(com.ghostmago.app.data.DeadManSwitch.message(context)) }
    var devMode by remember { mutableStateOf(AppState.devMode) }
    var strictAtt by remember { mutableStateOf(AppState.strictAttestation) }
    var licenseToken by remember { mutableStateOf("") }
    val deviceFp = remember { com.ghostmago.app.util.LicenseManager.deviceId(context) }
    var licensed by remember { mutableStateOf(com.ghostmago.app.util.LicenseManager.isLicensed(context)) }

    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        AppState.setBackground(context, uri)
        Toast.makeText(context, if (uri != null) "پسزمینه تنظیم شد" else "حذف شد", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات", color = TextPrimary) },
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
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Section("پروفایل")
            OutlinedTextField(value = nick, onValueChange = { nick = it },
                label = { Text("نام مستعار") }, modifier = Modifier.fillMaxWidth(),
                colors = fColors(), shape = RoundedCornerShape(12.dp))
            Text("نقش در مش", color = TextSecondary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(MeshRole.SUPREME, MeshRole.COMMANDER, MeshRole.LEADER, MeshRole.SOLDIER).forEach { r ->
                    FilterChip(selected = role == r, onClick = { role = r },
                        label = { Text("${r.symbol}", fontSize = 11.sp) })
                }
            }
            Text(role.fa, color = Accent, fontSize = 12.sp)
            if (role.requiresRolePassword()) {
                OutlinedTextField(
                    value = roleGatePw,
                    onValueChange = { roleGatePw = it },
                    label = { Text("رمز تأیید نقش") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fColors(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
            }
            Button(onClick = {
                if (role.requiresRolePassword() && !role.checkRolePassword(roleGatePw)) {
                    Toast.makeText(context, "رمز نقش نادرست", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                AppState.nickname = nick
                AppState.meshRole = role
                AppState.save(context)
                roleGatePw = ""
                Toast.makeText(context, "ذخیره شد", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)) { Text("ذخیره پروفایل") }

            Section("رمز ورود")
            OutlinedTextField(value = oldPw, onValueChange = { oldPw = it }, label = { Text("رمز فعلی") },
                modifier = Modifier.fillMaxWidth(), colors = fColors(), shape = RoundedCornerShape(12.dp),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            OutlinedTextField(value = newPw, onValueChange = { newPw = it }, label = { Text("رمز جدید") },
                modifier = Modifier.fillMaxWidth(), colors = fColors(), shape = RoundedCornerShape(12.dp),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            OutlinedTextField(value = confirmPw, onValueChange = { confirmPw = it }, label = { Text("تکرار") },
                modifier = Modifier.fillMaxWidth(), colors = fColors(), shape = RoundedCornerShape(12.dp),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            Button(onClick = {
                if (AppState.hashPassword(oldPw) != AppState.appPasswordHash) {
                    Toast.makeText(context, "رمز فعلی اشتباه", Toast.LENGTH_SHORT).show(); return@Button
                }
                if (newPw.length < 4 || newPw != confirmPw) {
                    Toast.makeText(context, "رمز جدید نامعتبر", Toast.LENGTH_SHORT).show(); return@Button
                }
                AppState.setPassword(context, newPw)
                oldPw = ""; newPw = ""; confirmPw = ""
                Toast.makeText(context, "رمز تغییر کرد", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Warning),
                shape = RoundedCornerShape(12.dp)) { Text("تغییر رمز ورود") }

            Section("رمز Duress (اضطراری)")
            Text("با این رمز وارد شوید تا حالت جعلی فعال شود.", color = TextSecondary, fontSize = 12.sp)
            OutlinedTextField(value = duressPw, onValueChange = { duressPw = it },
                label = { Text("رمز Duress (خالی = حذف)") }, modifier = Modifier.fillMaxWidth(),
                colors = fColors(), shape = RoundedCornerShape(12.dp),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            Button(onClick = {
                AppState.setDuressPassword(context, duressPw)
                duressPw = ""
                Toast.makeText(context, "Duress ذخیره شد", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
                shape = RoundedCornerShape(12.dp)) { Text("تنظیم Duress") }

            
            Section("ماشینحساب جعلی / ورود مخفی")
            Text("پین عددی برای باز کردن اپ از ماشینحساب (پیشفرض 0000)", color = TextSecondary, fontSize = 12.sp)
            OutlinedTextField(value = vaultPin, onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 12) vaultPin = it },
                label = { Text("پین ماشینحساب") }, modifier = Modifier.fillMaxWidth(),
                colors = fColors(), shape = RoundedCornerShape(12.dp))
            Button(onClick = {
                AppState.setVaultPin(context, vaultPin)
                Toast.makeText(context, "پین ذخیره شد", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)) { Text("ذخیره پین") }

            Section("کلمه امن (Safe Word)")
            Text("اگر تحت فشار این کلمه را بهعنوان رمز ورود بزنید، حالت جعلی فعال میشود.", color = TextSecondary, fontSize = 12.sp)
            OutlinedTextField(value = safeWord, onValueChange = { safeWord = it },
                label = { Text("کلمه امن") }, modifier = Modifier.fillMaxWidth(),
                colors = fColors(), shape = RoundedCornerShape(12.dp))
            Button(onClick = {
                AppState.setSafeWord(context, safeWord)
                Toast.makeText(context, "کلمه امن ذخیره شد", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Warning),
                shape = RoundedCornerShape(12.dp)) { Text("ذخیره کلمه امن") }

            Section("حالت نیرو (فقطخواندنی)")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("اجبار فقطخواندنی", color = TextPrimary)
                    Text("بدون ارسال گروهی", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(checked = readOnly, onCheckedChange = {
                    readOnly = it; AppState.forceReadOnly = it; AppState.save(context)
                })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("تأخیر تصادفی ارسال", color = TextPrimary)
                    Text("۰.۲–۲.۵ ثانیه برای مخفیکردن الگو", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(checked = rndDelay, onCheckedChange = {
                    rndDelay = it; AppState.randomSendDelay = it; AppState.save(context)
                })
            }

            
            
            
            
            Section("لایسنس سازمانی")
            Text("شناسه دستگاه: $deviceFp", color = TextSecondary, fontSize = 11.sp)
            Text(if (licensed) "✅ فعال" else "⚠ بدون لایسنس / دوره آزمایشی", color = if (licensed) Secondary else Warning, fontSize = 12.sp)
            OutlinedTextField(value = licenseToken, onValueChange = { licenseToken = it },
                label = { Text("توکن لایسنس") }, modifier = Modifier.fillMaxWidth(),
                colors = fColors(), shape = RoundedCornerShape(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    licensed = com.ghostmago.app.util.LicenseManager.activate(context, licenseToken)
                    Toast.makeText(context, if (licensed) "لایسنس فعال شد" else "نامعتبر", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                    Text("فعالسازی")
                }
                Button(onClick = {
                    val t = com.ghostmago.app.util.LicenseManager.generateForThisDevice(context, 365)
                    licenseToken = t
                    Toast.makeText(context, "توکن این دستگاه ساخته شد", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text("تولید توکن")
                }
            }

            
            
            Section("شبکه")
            OutlinedTextField(
                value = AppState.meshPort.toString(),
                onValueChange = {
                    val v = it.filter { c -> c.isDigit() }.toIntOrNull()
                    if (v != null) {
                        AppState.meshPort = v.coerceIn(1024, 65535)
                        AppState.save(context)
                    }
                },
                label = { Text("پورت مش UDP (پیشفرض ۵۵۹۹)") },
                modifier = Modifier.fillMaxWidth(),
                colors = fColors(),
                shape = RoundedCornerShape(12.dp)
            )
            Text("همه اعضا باید پورت یکسان داشته باشند", color = TextSecondary, fontSize = 11.sp)

            Section("Session security")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Auto-lock", color = TextPrimary)
                    Text("Lock vault after idle", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(checked = AppState.autoLockEnabled, onCheckedChange = {
                    AppState.autoLockEnabled = it; AppState.save(context)
                })
            }
            OutlinedTextField(
                value = AppState.sessionTimeoutMin.toString(),
                onValueChange = {
                    AppState.sessionTimeoutMin = it.filter { c -> c.isDigit() }.toIntOrNull()?.coerceIn(1, 120) ?: 15
                    AppState.save(context)
                },
                label = { Text("Idle timeout (minutes)") },
                modifier = Modifier.fillMaxWidth(),
                colors = fColors(),
                shape = RoundedCornerShape(12.dp)
            )

            Section("توسعه و attestation")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("حالت توسعهدهنده", color = TextPrimary)
                    Text("عدم مسدودسازی root در مش", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(checked = devMode, onCheckedChange = {
                    devMode = it; AppState.devMode = it; AppState.save(context)
                })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Attestation سخت", color = TextPrimary)
                Switch(checked = strictAtt, onCheckedChange = {
                    strictAtt = it; AppState.strictAttestation = it; AppState.save(context)
                })
            }

            Section("پروفایل عملیاتی")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                com.ghostmago.app.data.OpProfile.entries.forEach { pr ->
                    FilterChip(
                        selected = profile == pr.id,
                        onClick = {
                            profile = pr.id
                            AppState.opProfileId = pr.id
                            pr.applyTo(AppState)
                            AppState.save(context)
                            Toast.makeText(context, pr.fa, Toast.LENGTH_SHORT).show()
                        },
                        label = { Text(pr.fa, fontSize = 11.sp) }
                    )
                }
            }
            Text(com.ghostmago.app.data.OpProfile.from(profile).desc, color = TextSecondary, fontSize = 11.sp)

            Section("Keystore / StrongBox")
            Text(com.ghostmago.app.crypto.HardwareKeystore.status(context), color = TextSecondary, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("استفاده از کلید سختافزاری", color = TextPrimary)
                Switch(checked = hwKs, onCheckedChange = {
                    hwKs = it; AppState.useHardwareKeystore = it; AppState.save(context)
                    if (it) com.ghostmago.app.crypto.HardwareKeystore.ensureKey(true)
                })
            }

            Section("Sealed Sender")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("فرستنده ناشناس در شبکه", color = TextPrimary)
                    Text("ناظر ترافیک هویت را نمیبیند", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(checked = sealedOn, onCheckedChange = {
                    sealedOn = it; AppState.sealedSender = it; AppState.save(context)
                })
            }

            Section("کلید مرگ (Dead-man)")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("فعال", color = TextPrimary)
                Switch(checked = dmsOn, onCheckedChange = {
                    dmsOn = it
                    if (it) com.ghostmago.app.data.DeadManSwitch.enable(context, dmsMin, dmsMsg)
                    else com.ghostmago.app.data.DeadManSwitch.disable(context)
                })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 60, 180, 360).forEach { m ->
                    FilterChip(selected = dmsMin == m, onClick = {
                        dmsMin = m
                        if (dmsOn) com.ghostmago.app.data.DeadManSwitch.enable(context, m, dmsMsg)
                    }, label = { Text("${m}د") })
                }
            }
            OutlinedTextField(value = dmsMsg, onValueChange = { dmsMsg = it },
                label = { Text("پیام در صورت عدم چکاین") }, modifier = Modifier.fillMaxWidth(),
                colors = fColors(), shape = RoundedCornerShape(12.dp))
            Button(onClick = {
                com.ghostmago.app.data.DeadManSwitch.checkIn(context)
                Toast.makeText(context, "چکاین ثبت شد", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Warning),
                shape = RoundedCornerShape(12.dp)) { Text("چکاین الان") }

            Section("ترافیک پوششی (Cover Traffic)")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("فعالسازی مداوم", color = TextPrimary)
                    Text("بستههای جعلی دورهای روی مش", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(checked = coverOn, onCheckedChange = {
                    coverOn = it; AppState.coverTraffic = it; AppState.save(context)
                })
            }
            Text("فاصله ارسال پوششی", color = TextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 10, 15, 30, 60).forEach { sec ->
                    FilterChip(selected = coverSec == sec, onClick = {
                        coverSec = sec; AppState.coverIntervalSec = sec; AppState.save(context)
                    }, label = { Text("${sec}s") })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("تأیید اجباری Safety Number", color = TextPrimary)
                    Text("قبل از چت خصوصی", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(checked = reqSafety, onCheckedChange = {
                    reqSafety = it; AppState.requireSafetyNumber = it; AppState.save(context)
                })
            }

            Section("مش و حریم")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("سکوت رادیویی", color = TextPrimary)
                    Text("بدون Beacon خروجی", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(checked = radioSilence, onCheckedChange = {
                    radioSilence = it; AppState.radioSilence = it; AppState.save(context)
                })
            }
            var relayOn by remember { mutableStateOf(AppState.relayEnabled) }
            var relayHost by remember { mutableStateOf(AppState.relayHost) }
            var btOn by remember { mutableStateOf(AppState.useBluetoothMesh) }
            var hideCode by remember { mutableStateOf(AppState.hideIconCode) }
            var showCode by remember { mutableStateOf(AppState.showIconCode) }
            Text("رله اینترنت (شهرهای مختلف)", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text("آدرس مثل my.server.com:7777 — سرور TCP که هر خط base64 را به بقیه میفرستد", color = TextSecondary, fontSize = 11.sp)
            OutlinedTextField(value = relayHost, onValueChange = { relayHost = it }, label = { Text("میزبان:پورت رله") },
                modifier = Modifier.fillMaxWidth(), colors = fColors(), shape = RoundedCornerShape(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("فعالسازی رله اینترنت", color = TextPrimary)
                Switch(checked = relayOn, onCheckedChange = {
                    relayOn = it; AppState.relayEnabled = it; AppState.relayHost = relayHost; AppState.save(context)
                })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("مش بلوتوث", color = TextPrimary)
                    Text("اگر وایفای مشغول بود از بلوتوث جفتشده استفاده شود", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(checked = btOn, onCheckedChange = {
                    btOn = it; AppState.useBluetoothMesh = it; AppState.save(context)

            var usbOn by remember { mutableStateOf(AppState.useUsbRadioMesh) }
            var usbBand by remember { mutableStateOf(AppState.usbRadioBand) }
            var usbText by remember { mutableStateOf(AppState.usbRadioTextOnly) }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("مش رادیو USB (FM/AM)", color = TextPrimary)
                    Text(
                        "ماژول لحیم‌شده/دانگل روی تایپ‌سی — وقتی وای‌فای و بلوتوث در دسترس نیست",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = usbOn,
                    onCheckedChange = {
                        usbOn = it
                        AppState.useUsbRadioMesh = it
                        AppState.save(context)
                    }
                )
            }
            if (usbOn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = usbBand == "fm",
                        onClick = {
                            usbBand = "fm"
                            AppState.usbRadioBand = "fm"
                            AppState.save(context)
                        },
                        label = { Text("FM") }
                    )
                    FilterChip(
                        selected = usbBand == "am",
                        onClick = {
                            usbBand = "am"
                            AppState.usbRadioBand = "am"
                            AppState.save(context)
                        },
                        label = { Text("AM") }
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("فقط متن روی رادیو (بدون فایل حجیم)", color = TextSecondary, fontSize = 12.sp)
                    Switch(
                        checked = usbText,
                        onCheckedChange = {
                            usbText = it
                            AppState.usbRadioTextOnly = it
                            AppState.save(context)
                        }
                    )
                }
                Text(
                    "ماژول باید از USB به‌صورت سریال/Bulk دیده شود. گوشی FM فرستنده داخلی ندارد.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
                })
            }

            
            Text("شبکه و ترافیک سازمانی", color = TextPrimary, fontWeight = FontWeight.Bold)
            var blockRelay by remember { mutableStateOf(AppState.orgBlockInternetRelay) }
            var reqCtrl by remember { mutableStateOf(AppState.requireControlSignatures) }
            var sepCtrl by remember { mutableStateOf(AppState.separateControlPlane) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("مسدود کردن رله اینترنت", color = TextPrimary)
                    Text("پروفایل دولتی — فقط شبکه محلی", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(checked = blockRelay, onCheckedChange = {
                    blockRelay = it; AppState.orgBlockInternetRelay = it; AppState.save(context)
                })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("امضای بسته‌های کنترل", color = TextPrimary)
                    Text("join/approve/reject با HMAC", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(checked = reqCtrl, onCheckedChange = {
                    reqCtrl = it; AppState.requireControlSignatures = it; AppState.save(context)
                })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("جداسازی کانال کنترل", color = TextPrimary)
                    Text("پورت داده + ۱ برای مدیریت", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(checked = sepCtrl, onCheckedChange = {
                    sepCtrl = it; AppState.separateControlPlane = it; AppState.save(context)
                })
            }
            var bands by remember { mutableStateOf(AppState.allowedUsbRadioBands) }
            OutlinedTextField(
                value = bands,
                onValueChange = {
                    bands = it
                    AppState.allowedUsbRadioBands = it
                    AppState.save(context)
                },
                label = { Text("باندهای مجاز رادیو USB (مثل fm,am)") },
                modifier = Modifier.fillMaxWidth(),
                colors = fColors(),
                shape = RoundedCornerShape(12.dp)
            )

            Text("پروفایل انتقال مش", color = TextPrimary, fontWeight = FontWeight.Bold)
            var tMode by remember { mutableStateOf(AppState.transportMode) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    "auto" to "خودکار",
                    "wifi" to "وای‌فای",
                    "bt" to "بلوتوث",
                    "usb" to "رادیو USB"
                ).forEach { (id, label) ->
                    FilterChip(
                        selected = tMode == id,
                        onClick = {
                            tMode = id
                            AppState.transportMode = id
                            AppState.save(context)
                        },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
            Text(
                "خودکار: وای‌فای + بلوتوث/رادیو در صورت فعال بودن در تنظیمات",
                color = TextSecondary,
                fontSize = 11.sp
            )

            Text("پشتیبان کلید مشکات", color = TextPrimary, fontWeight = FontWeight.Bold)
            var backupPw by remember { mutableStateOf("") }
            var backupBlob by remember { mutableStateOf("") }
            OutlinedTextField(
                value = backupPw,
                onValueChange = { backupPw = it },
                label = { Text("رمز پشتیبان (حداقل ۶)") },
                modifier = Modifier.fillMaxWidth(),
                colors = fColors(),
                shape = RoundedCornerShape(12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        try {
                            val out = com.ghostmago.app.crypto.KeyBackup.export(context, backupPw)
                            backupBlob = out
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("mishkat-backup", out))
                            Toast.makeText(context, "پشتیبان ساخته و در کلیپ‌بورد کپی شد", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "خطا", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) { Text("ساخت پشتیبان", fontSize = 12.sp) }
                Button(
                    onClick = {
                        try {
                            val blob = backupBlob.ifBlank {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            }
                            val msg = com.ghostmago.app.crypto.KeyBackup.import(context, backupPw, blob)
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "خطا", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Secondary)
                ) { Text("بازیابی", fontSize = 12.sp) }
            }
            OutlinedTextField(
                value = backupBlob,
                onValueChange = { backupBlob = it },
                label = { Text("متن پشتیبان (جای‌گذاری)") },
                modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                colors = fColors(),
                shape = RoundedCornerShape(12.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Safety Number اجباری", color = TextPrimary)
                    Text("قبل از چت خصوصی باید رو در رو تأیید شود", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = AppState.requireSafetyNumber,
                    onCheckedChange = {
                        AppState.requireSafetyNumber = it
                        AppState.save(context)
                    }
                )
            }

            Text("مخفیسازی آیکون مشکات", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = hideCode, onValueChange = { hideCode = it.filter { c -> c.isDigit() }.take(8) },
                label = { Text("کد مخفی شمارهگیر (1234)") }, modifier = Modifier.fillMaxWidth(), colors = fColors(), shape = RoundedCornerShape(12.dp))
            OutlinedTextField(value = showCode, onValueChange = { showCode = it.filter { c -> c.isDigit() }.take(8) },
                label = { Text("کد نمایش شمارهگیر (4321)") }, modifier = Modifier.fillMaxWidth(), colors = fColors(), shape = RoundedCornerShape(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    AppState.hideIconCode = hideCode.ifBlank { "1234" }
                    AppState.showIconCode = showCode.ifBlank { "4321" }
                    AppState.save(context)
                    com.ghostmago.app.util.IconHider.hide(context)
                    android.widget.Toast.makeText(context, "آیکون مخفی شد", android.widget.Toast.LENGTH_SHORT).show()
                }, colors = ButtonDefaults.buttonColors(containerColor = Danger), shape = RoundedCornerShape(12.dp)) { Text("مخفی الآن") }
                Button(onClick = {
                    AppState.hideIconCode = hideCode.ifBlank { "1234" }
                    AppState.showIconCode = showCode.ifBlank { "4321" }
                    AppState.save(context)
                    com.ghostmago.app.util.IconHider.show(context)
                    android.widget.Toast.makeText(context, "آیکون نمایان شد", android.widget.Toast.LENGTH_SHORT).show()
                }, colors = ButtonDefaults.buttonColors(containerColor = Secondary), shape = RoundedCornerShape(12.dp)) { Text("نمایش آیکون") }
            }
            Text("پیام ناپدیدشونده پیشفرض (ثانیه، ۰=خاموش)", color = TextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 30, 60, 300).forEach { s ->
                    FilterChip(selected = ephSec == s, onClick = {
                        ephSec = s; AppState.ephemeralDefaultSec = s; AppState.save(context)
                    }, label = { Text(if (s == 0) "خاموش" else "${s}s") })
                }
            }

            Section("ظاهر")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("تم تاریک", color = TextPrimary)
                Switch(checked = dark, onCheckedChange = {
                    dark = it; AppState.darkTheme = it; AppState.save(context)
                })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("آبی", "سبز", "بنفش", "نارنجی").forEachIndexed { i, name ->
                    FilterChip(selected = accent == i, onClick = {
                        accent = i; AppState.accentColor = i; AppState.save(context)
                    }, label = { Text(name) })
                }
            }
            Button(onClick = { bgPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(12.dp)) { Text("تغییر عکس پسزمینه") }
            if (AppState.bgImagePath != null) {
                TextButton(onClick = {
                    AppState.setBackground(context, null)
                }) { Text("حذف پسزمینه", color = Danger) }
            }

            Section("لاگ امنیتی")
            val log = remember { SecurityUtils.getAuditLog(context) }
            Text(if (log.isBlank()) "خالی" else log.takeLast(500), color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable private fun Section(t: String) {
    Spacer(Modifier.height(6.dp))
    Text(t, color = Primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
}

@Composable private fun fColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary, unfocusedBorderColor = Border,
    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
    cursorColor = Primary, focusedContainerColor = CardDark, unfocusedContainerColor = CardDark,
    focusedLabelColor = Primary, unfocusedLabelColor = TextSecondary
)
