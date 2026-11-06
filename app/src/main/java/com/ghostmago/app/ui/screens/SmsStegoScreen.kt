package com.ghostmago.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ghostmago.app.stego.StegoEngine
import com.ghostmago.app.ui.theme.*
import com.ghostmago.app.util.SecurityUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SmsHit(
    val address: String,
    val body: String,
    val date: Long,
    val preview: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsStegoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var cover by remember { mutableStateOf(StegoEngine.getSmsCover(0)) }
    var secret by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var coverIdx by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf(listOf<SmsHit>()) }
    var selected by remember { mutableStateOf<SmsHit?>(null) }
    var decodePw by remember { mutableStateOf("") }
    var decoded by remember { mutableStateOf("") }

    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idIdx = it.getColumnIndex(ContactsContract.Contacts._ID)
                    val id = it.getString(idIdx)
                    val hasPhoneIdx = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                    val hasPhone = if (hasPhoneIdx >= 0) it.getInt(hasPhoneIdx) else 0
                    if (hasPhone > 0) {
                        val phones = context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            arrayOf(id),
                            null
                        )
                        phones?.use { p ->
                            if (p.moveToFirst()) {
                                val numIdx = p.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                val num = if (numIdx >= 0) p.getString(numIdx) else null
                                phone = num?.replace(Regex("[^0-9+]"), "") ?: ""
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { !it }) {
            Toast.makeText(context, "SMS/Contacts permission required", Toast.LENGTH_LONG).show()
        }
    }

    fun ensurePerms(vararg perms: String): Boolean {
        val need = perms.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) {
            permissionLauncher.launch(need.toTypedArray())
            return false
        }
        return true
    }

    fun scanInbox() {
        if (!ensurePerms(Manifest.permission.READ_SMS)) return
        val list = mutableListOf<SmsHit>()
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                null,
                null,
                Telephony.Sms.DATE + " DESC"
            )
            cursor?.use {
                var count = 0
                while (it.moveToNext() && count < 300) {
                    val addr = it.getString(0) ?: ""
                    val body = it.getString(1) ?: ""
                    val date = it.getLong(2)
                    if (StegoEngine.looksLikeStego(body)) {
                        list.add(
                            SmsHit(
                                addr,
                                body,
                                date,
                                body.take(40).replace(Regex("[\\u200B-\\u200F\\uFEFF]"), "")
                            )
                        )
                        count++
                    }
                }
            }
            hits = list
            status = "${list.size} پیام رمزدار پیدا شد"
        } catch (e: Exception) {
            status = e.message ?: "scan error"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پنهاننگاری پیامک", color = TextPrimary) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("ارسال", color = Accent, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = cover, onValueChange = { cover = it },
                label = { Text("متن پوششی") },
                modifier = Modifier.fillMaxWidth(),
                colors = smsColors(),
                shape = RoundedCornerShape(12.dp),
                minLines = 2
            )
            OutlinedTextField(
                value = secret, onValueChange = { secret = it },
                label = { Text("پیام مخفی") },
                modifier = Modifier.fillMaxWidth(),
                colors = smsColors(),
                shape = RoundedCornerShape(12.dp),
                minLines = 2
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("رمز") },
                modifier = Modifier.fillMaxWidth(),
                colors = smsColors(),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("شماره تلفن") },
                modifier = Modifier.fillMaxWidth(),
                colors = smsColors(),
                shape = RoundedCornerShape(12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (ensurePerms(Manifest.permission.READ_CONTACTS)) {
                            contactPicker.launch(null)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Secondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Contacts, null)
                    Spacer(Modifier.width(6.dp))
                    Text("مخاطب")
                }
                Button(
                    onClick = {
                        try {
                            if (secret.isBlank() || password.isBlank()) {
                                status = "پیام مخفی و رمز لازم است"
                                return@Button
                            }
                            val c = cover.ifBlank { StegoEngine.getSmsCover(coverIdx) }
                            preview = StegoEngine.encodeSms(c, secret, password)
                            status = "رمزگذاری شد (فرمت پیامک) — طول: ${preview.length}"
                        } catch (e: Exception) {
                            status = e.message ?: "error"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("رمزگذاری") }
                Button(
                    onClick = {
                        if (!ensurePerms(Manifest.permission.SEND_SMS)) return@Button
                        if (phone.isBlank()) {
                            status = "شماره لازم است"
                            return@Button
                        }
                        try {
                            if (secret.length > 120) {
                                status = "پیام مخفی حداکثر ۱۲۰ کاراکتر"
                                return@Button
                            }
                            val body = if (preview.isNotBlank()) preview else StegoEngine.encodeSms(
                                cover.ifBlank { StegoEngine.getSmsCover(0) },
                                secret,
                                password
                            )
                            val sms = if (android.os.Build.VERSION.SDK_INT >= 31) {
                                context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
                            } else {
                                @Suppress("DEPRECATION")
                                SmsManager.getDefault()
                            }
                            val parts = ArrayList(sms.divideMessage(body) ?: arrayListOf(body))
                            if (parts.isEmpty()) parts.add(body)
                            // Always use multipart API when >1; stays as SMS (not RCS chat)
                            if (parts.size == 1) {
                                sms.sendTextMessage(phone, null, parts[0], null, null)
                            } else {
                                sms.sendMultipartTextMessage(phone, null, parts, null, null)
                            }
                            SecurityUtils.saveStat(context, "sms_sent")
                            status = "پیامک SMS ارسال شد — ${parts.size} بخش (متن عادی پیامک)"
                            Toast.makeText(context, "ارسال پیامک: ${parts.size} بخش", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            status = e.message ?: "send fail"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Send, null)
                    Spacer(Modifier.width(4.dp))
                    Text("ارسال")
                }
            }
            if (preview.isNotBlank()) {
                Text("Preview:", color = TextSecondary, fontSize = 12.sp)
                Text(preview.take(120), color = TextPrimary, fontSize = 12.sp)
            }
            if (status.isNotBlank()) Text(status, color = Secondary, fontSize = 13.sp)

            HorizontalDivider(color = Border)
            Text("جستجوی پیامکهای مخفی", color = Accent, fontWeight = FontWeight.Bold)
            Button(
                onClick = { scanInbox() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(8.dp))
                Text("جستجوی پیامهای رمزدار")
            }
            val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
            hits.forEach { hit ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    modifier = Modifier.fillMaxWidth().clickable { selected = hit; decoded = "" },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(hit.address, color = Primary, fontWeight = FontWeight.Bold)
                        Text(fmt.format(Date(hit.date)), color = TextSecondary, fontSize = 11.sp)
                        Text(hit.preview, color = TextPrimary, fontSize = 12.sp)
                    }
                }
            }
            selected?.let { hit ->
                Text("رمزگشایی انتخابشده", color = TextPrimary, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = decodePw, onValueChange = { decodePw = it },
                    label = { Text("رمز") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = smsColors(),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        try {
                            decoded = StegoEngine.decode(hit.body, decodePw)
                        } catch (e: Exception) {
                            decoded = "Error: ${e.message}"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Secondary)
                ) { Text("رمزگشایی") }
                if (decoded.isNotBlank()) Text(decoded, color = Accent)
            }
        }
    }
}

@Composable
private fun smsColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = Border,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Primary,
    focusedContainerColor = CardDark,
    unfocusedContainerColor = CardDark
)
