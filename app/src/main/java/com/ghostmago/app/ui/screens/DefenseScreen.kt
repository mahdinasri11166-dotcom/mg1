package com.ghostmago.app.ui.screens

import android.Manifest
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostmago.app.data.AppState
import com.ghostmago.app.security.*
import com.ghostmago.app.ui.theme.*
import com.ghostmago.app.util.PermissionHub
import com.ghostmago.app.util.ShareHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefenseScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("نفوذ", "پاکسازی", "رمز فایل", "خزانه", "یکپارچگی")

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val denied = res.filter { !it.value }.keys
        if (denied.isNotEmpty()) {
            Toast.makeText(context, "برخی مجوزها رد شد — هنگام نیاز دوباره درخواست میشود", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val need = PermissionHub.missing(context, *PermissionHub.allRuntime())
        if (need.isNotEmpty()) permLauncher.launch(need)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("امنیت دفاعی — مشکات", color = TextPrimary, fontWeight = FontWeight.Bold) },
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
                .imePadding()
        ) {
            ScrollableTabRow(selectedTabIndex = tab, containerColor = SurfaceDark, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (tab) {
                    0 -> IntrusionTab()
                    1 -> WipeTab()
                    2 -> FileCryptoTab()
                    3 -> VaultTab()
                    4 -> IntegrityTab()
                }
            }
        }
    }
}

@Composable
private fun IntrusionTab() {
    val context = LocalContext.current
    var findings by remember { mutableStateOf(listOf<SecurityFinding>()) }
    var scanning by remember { mutableStateOf(false) }

    Text("تشخیص نفوذ (سطح دستگاه)", color = TextPrimary, fontWeight = FontWeight.Bold)
    Text(
        "روت، بستههای مشکوک شناختهشده، دیباگ، دسترسپذیری و نشانههای شبیهساز را بررسی میکند. جایگزین آنتیویروس کامل نیست.",
        color = TextSecondary,
        fontSize = 13.sp
    )
    Button(
        onClick = {
            scanning = true
            findings = IntrusionMonitor.scan(context)
            scanning = false
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Primary),
        shape = RoundedCornerShape(12.dp)
    ) { Text(if (scanning) "در حال بررسی…" else "شروع اسکن امنیتی") }

    findings.forEach { f ->
        val col = when (f.level) {
            "critical" -> Danger
            "warn" -> Warning
            else -> Secondary
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(f.title, color = col, fontWeight = FontWeight.Bold)
                Text(f.detail, color = TextPrimary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun WipeTab() {
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val dir = File(context.cacheDir, "wipe").apply { mkdirs() }
            val tmp = File(dir, "to_wipe_${System.currentTimeMillis()}.bin")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            val ok = SecureWipe.wipe(tmp, passes = 3)
            status = if (ok) "پاکسازی امن انجام شد (۳ گذر بازنویسی)" else "پاکسازی ناموفق"
            Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            status = e.message ?: "خطا"
        }
    }
    Text("پاکسازی امن فایل", color = TextPrimary, fontWeight = FontWeight.Bold)
    Text(
        "فایل انتخابی ابتدا در حافظه موقت کپی و چندبار بازنویسی تصادفی میشود، سپس حذف میگردد. روی حافظه فلش بازیابی مطلق تضمین نیست.",
        color = TextSecondary,
        fontSize = 13.sp
    )
    Button(
        onClick = { picker.launch("*/*") },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Danger),
        shape = RoundedCornerShape(12.dp)
    ) { Text("انتخاب و پاکسازی امن") }
    if (status.isNotBlank()) Text(status, color = Secondary)
}

@Composable
private fun FileCryptoTab() {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("enc") }
    var status by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (password.length < 6) {
            Toast.makeText(context, "رمز حداقل ۶ کاراکتر", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        try {
            val dir = File(context.filesDir, "filecrypto").apply { mkdirs() }
            val input = File(dir, "in_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { i ->
                input.outputStream().use { o -> i.copyTo(o) }
            }
            if (mode == "enc") {
                val out = File(dir, "encrypted_${System.currentTimeMillis()}.gm")
                FileCrypto.encryptFile(input, out, password)
                status = "رمز شد: ${out.name}"
                ShareHelper.shareFile(context, out, "application/octet-stream")
            } else {
                val out = File(dir, "decrypted_${System.currentTimeMillis()}.bin")
                FileCrypto.decryptFile(input, out, password)
                status = "رمزگشایی شد: ${out.name}"
                ShareHelper.shareFile(context, out, "application/octet-stream")
            }
            input.delete()
        } catch (e: Exception) {
            status = "خطا: ${e.message}"
        }
    }
    Text("رمزنگاری فایل شخصی (AES-256)", color = TextPrimary, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = mode == "enc", onClick = { mode = "enc" }, label = { Text("رمزنگاری") })
        FilterChip(selected = mode == "dec", onClick = { mode = "dec" }, label = { Text("رمزگشایی") })
    }
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("رمز فایل") },
        modifier = Modifier.fillMaxWidth(),
        colors = defColors(),
        shape = RoundedCornerShape(12.dp)
    )
    Button(
        onClick = { picker.launch("*/*") },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Primary),
        shape = RoundedCornerShape(12.dp)
    ) { Text(if (mode == "enc") "انتخاب فایل و رمزنگاری" else "انتخاب فایل و رمزگشایی") }
    if (status.isNotBlank()) Text(status, color = Secondary)
}

@Composable
private fun VaultTab() {
    val context = LocalContext.current
    val hash = AppState.appPasswordHash
    var title by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(PasswordVault.list(context, hash)) }

    Text("مدیر رمز (خزانه امن)", color = TextPrimary, fontWeight = FontWeight.Bold)
    Text("دادهها با کلید مشتقشده از رمز ورود اپ رمز میشوند.", color = TextSecondary, fontSize = 13.sp)
    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان") }, modifier = Modifier.fillMaxWidth(), colors = defColors(), shape = RoundedCornerShape(12.dp))
    OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("نام کاربری") }, modifier = Modifier.fillMaxWidth(), colors = defColors(), shape = RoundedCornerShape(12.dp))
    OutlinedTextField(value = secret, onValueChange = { secret = it }, label = { Text("رمز") }, modifier = Modifier.fillMaxWidth(), colors = defColors(), shape = RoundedCornerShape(12.dp))
    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("یادداشت") }, modifier = Modifier.fillMaxWidth(), colors = defColors(), shape = RoundedCornerShape(12.dp))
    Button(
        onClick = {
            if (title.isBlank() || secret.isBlank()) {
                Toast.makeText(context, "عنوان و رمز لازم است", Toast.LENGTH_SHORT).show()
                return@Button
            }
            PasswordVault.add(context, hash, title, user, secret, note)
            title = ""; user = ""; secret = ""; note = ""
            entries = PasswordVault.list(context, hash)
            Toast.makeText(context, "ذخیره شد", Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Primary),
        shape = RoundedCornerShape(12.dp)
    ) { Text("افزودن به خزانه") }

    entries.forEach { e ->
        Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(e.title, color = Accent, fontWeight = FontWeight.Bold)
                Text("کاربر: ${e.username}", color = TextPrimary, fontSize = 13.sp)
                Text("رمز: ${e.secret}", color = TextPrimary, fontSize = 13.sp)
                if (e.note.isNotBlank()) Text(e.note, color = TextSecondary, fontSize = 12.sp)
                TextButton(onClick = {
                    PasswordVault.remove(context, hash, e.id)
                    entries = PasswordVault.list(context, hash)
                }) { Text("حذف", color = Danger) }
            }
        }
    }
}

@Composable
private fun IntegrityTab() {
    val context = LocalContext.current
    var label by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<FileIntegrity.CheckResult>()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val dir = File(context.filesDir, "integrity").apply { mkdirs() }
            val f = File(dir, "watch_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { i ->
                f.outputStream().use { o -> i.copyTo(o) }
            }
            FileIntegrity.watch(context, f.absolutePath, label.ifBlank { f.name })
            label = ""
            Toast.makeText(context, "چکسام ثبت شد", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
        }
    }
    Text("سلامت فایل (یکپارچگی)", color = TextPrimary, fontWeight = FontWeight.Bold)
    Text("هش SHA-256 ذخیره میشود؛ در بررسی بعدی تغییر یا حذف هشدار میدهد.", color = TextSecondary, fontSize = 13.sp)
    OutlinedTextField(
        value = label,
        onValueChange = { label = it },
        label = { Text("برچسب فایل") },
        modifier = Modifier.fillMaxWidth(),
        colors = defColors(),
        shape = RoundedCornerShape(12.dp)
    )
    Button(
        onClick = { picker.launch("*/*") },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Primary),
        shape = RoundedCornerShape(12.dp)
    ) { Text("انتخاب و ثبت فایل") }
    Button(
        onClick = { results = FileIntegrity.verifyAll(context) },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Secondary),
        shape = RoundedCornerShape(12.dp)
    ) { Text("بررسی همه فایلهای ثبتشده") }
    results.forEach { r ->
        Text(
            "${r.label}: ${r.message}",
            color = if (r.ok) Secondary else Danger,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun defColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = Border,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Primary,
    focusedContainerColor = CardDark,
    unfocusedContainerColor = CardDark
)
