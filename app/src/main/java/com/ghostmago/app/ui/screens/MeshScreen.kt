package com.ghostmago.app.ui.screens

import com.ghostmago.app.ui.screens.mesh.ChatMessage
import com.ghostmago.app.ui.screens.mesh.MeshBubble
import com.ghostmago.app.mesh.MeshModule
import com.ghostmago.app.mesh.TransportProfile
import com.ghostmago.app.data.VerifiedPeers
import com.ghostmago.app.data.RetentionPolicy
import com.ghostmago.app.data.MessageClassification
import com.ghostmago.app.data.ImmutableAudit
import com.ghostmago.app.data.RoomStore
import com.ghostmago.app.mesh.MeshMessageStore

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ghostmago.app.data.AppState
import com.ghostmago.app.util.MeshConnectivity
import com.ghostmago.app.data.MeshRole
import com.ghostmago.app.data.ChatContacts
import com.ghostmago.app.data.AuditLog
import com.ghostmago.app.mesh.InviteQr
import com.ghostmago.app.mesh.MeshNetwork
import com.ghostmago.app.mesh.MeshPacket
import com.ghostmago.app.mesh.PrivateSessionStore
import com.ghostmago.app.ui.theme.*
import com.ghostmago.app.util.ImageCompress
import com.ghostmago.app.util.VoiceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshScreen(onBack: () -> Unit, nav: androidx.navigation.NavController? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Soft error surface — avoid silent crash on mesh UI jobs
    val meshUiHandler = remember {
        kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
            android.util.Log.e("MeshScreen", "UI job", e)
        }
    }
    var nickname by remember {
        mutableStateOf(
            AppState.lastMeshNickname.ifBlank { AppState.nickname }.ifBlank { "کاربر" }
        )
    }
    var groupId by remember {
        mutableStateOf(
            AppState.lastGroupId.ifBlank { AppState.groupId.ifBlank { "default" } }
        )
    }
    var groupPassword by remember {
        mutableStateOf(AppState.lastGroupPassword.ifBlank { AppState.pendingMeshPassword })
    }
    var myId by remember { mutableStateOf("") }
    var isSetup by remember { mutableStateOf(false) }
    var mesh by remember { mutableStateOf<MeshNetwork?>(null) }
    var status by remember { mutableStateOf("غیرفعال") }
    val msgStore = remember { MeshMessageStore(30) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var memberCount by remember { mutableIntStateOf(1) }
    var onlineCount by remember { mutableIntStateOf(0) }
    var membersList by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var memberStatuses by remember { mutableStateOf(listOf<MeshNetwork.MemberStatus>()) }
    var pendingList by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var showPending by remember { mutableStateOf(false) }
    var privatePeer by remember { mutableStateOf<String?>(null) }
    var privatePeerNick by remember { mutableStateOf("") }
        var sessionFp by remember { mutableStateOf("") }
    var safetyVerified by remember { mutableStateOf(false) }
    var safetyInput by remember { mutableStateOf("") }
    var chatTab by remember { mutableIntStateOf(0) }
    val readOnlyMesh = !AppState.canSendGroup()
    var roomsTick by remember { mutableIntStateOf(0) }
    val roomsList = remember(roomsTick) { RoomStore.list() }
    var inviteBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showInviteQr by remember { mutableStateOf(false) }
    var inviteTtl by remember { mutableIntStateOf(AppState.inviteTtlSec) }
    var inviteHoursCustom by remember { mutableStateOf("") }
    var shouldAutoConnect by remember { mutableStateOf(false) }
    var waitingApproval by remember { mutableStateOf(false) }
    var membershipOk by remember { mutableStateOf(false) }
    var roleSecretInput by remember { mutableStateOf("") }
    var pendingRole by remember { mutableStateOf<MeshRole?>(null) }
    var showRoleSecretDialog by remember { mutableStateOf(false) }
    var rolePwDialog by remember { mutableStateOf<MeshRole?>(null) }
    var rolePwInput by remember { mutableStateOf("") }
    var showJoinReqDialog by remember { mutableStateOf(false) }
    var joinReqNick by remember { mutableStateOf("") }
    var joinReqHash by remember { mutableStateOf("") }
    

    // Re-read each composition; AppState.meshRole is mutableState
    val myRole = AppState.meshRole
    // میزبان شبکه یا فرمانده — همیشه میتواند تأیید/رد کند
    val canManageJoins = myRole.canApprove() || (isSetup && membershipOk && !waitingApproval)

    if (showJoinReqDialog && joinReqHash.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("درخواست عضویت جدید", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Text(
                    "کاربر «$joinReqNick» میخواهد وارد گروه شود.\nتأیید = عضو میشود  |  رد = ورود ممنوع",
                    fontSize = 15.sp,
                    lineHeight = 24.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            mesh?.approveMember(joinReqHash, "soldier", joinReqNick)
                            ChatContacts.save(context, joinReqHash, joinReqNick)
                            pendingList = pendingList.filter { it.first != joinReqHash }
                            showJoinReqDialog = false
                            joinReqHash = ""
                            Toast.makeText(context, "$joinReqNick تأیید و عضو شد", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "خطا", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Secondary)
                ) { Text("✓ تأیید عضویت", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Button(
                    onClick = {
                        try {
                            mesh?.rejectMember(joinReqHash)
                            pendingList = pendingList.filter { it.first != joinReqHash }
                            showJoinReqDialog = false
                            joinReqHash = ""
                            Toast.makeText(context, "عضویت رد شد", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "خطا", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) { Text("✗ رد عضویت", fontWeight = FontWeight.Bold) }
            }
        )
    }


    if (rolePwDialog != null) {
        AlertDialog(
            onDismissRequest = { rolePwDialog = null; rolePwInput = "" },
            title = { Text("تأیید نقش ${rolePwDialog?.fa}") },
            text = {
                OutlinedTextField(
                    value = rolePwInput,
                    onValueChange = { rolePwInput = it },
                    label = { Text("رمز نقش") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val r = rolePwDialog
                    if (r != null && r.checkRolePassword(rolePwInput)) {
                        AppState.meshRole = r
                        AppState.save(context)
                        rolePwDialog = null
                        rolePwInput = ""
                        Toast.makeText(context, "نقش ${r.fa} فعال شد", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "رمز نقش نادرست", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("تأیید") }
            },
            dismissButton = {
                TextButton(onClick = { rolePwDialog = null; rolePwInput = "" }) { Text("لغو") }
            }
        )
    }

    LaunchedEffect(Unit) {
        if (AppState.pendingMeshAutoJoin) {
            val pw = AppState.pendingMeshPassword
            if (pw.isNotBlank()) groupPassword = pw
            groupId = AppState.groupId.ifBlank { groupId }
            AppState.pendingMeshAutoJoin = false
            AppState.pendingMeshPassword = ""
            AppState.save(context)
            shouldAutoConnect = true
            waitingApproval = true
            android.widget.Toast.makeText(context, "دعوت بارگذاری شد — اتصال خودکار…", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val listState = rememberLazyListState()
    val voiceManager = remember { VoiceManager(context) }

    val meshPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale("fa")) }

    val inviteScanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val bmp = android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            val raw = com.ghostmago.app.util.QrHelper.decode(bmp) ?: run {
                Toast.makeText(context, "بارکد خوانده نشد", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            val inv = InviteQr.parse(raw)
            if (inv == null) {
                Toast.makeText(context, "بارکد دعوت نامعتبر یا منقضی", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }
            groupId = inv.groupId
            groupPassword = inv.groupPassword
            AppState.groupId = inv.groupId
            AppState.save(context)
            waitingApproval = true  // guest via invite — any role
            shouldAutoConnect = true
            Toast.makeText(
                context,
                "دعوت از ${inv.commanderNick} — اتصال خودکار و انتظار تأیید",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "خطا", Toast.LENGTH_SHORT).show()
        }
    }

    val imagePickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null || mesh == null) return@rememberLauncherForActivityResult
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@rememberLauncherForActivityResult
            val bytes = input.readBytes().also { input.close() }
            val data = ImageCompress.toJpeg(bytes, 28000)
            val mid = mesh?.sendImage(data) ?: ""
            messages = msgStore.add(ChatMessage(
                origin = "me", nick = nickname, text = "📷 عکس",
                isMine = true, imageBytes = data, deliveryStatus = "sent", msgId = mid
            ))
        } catch (e: Exception) {
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null || mesh == null) return@rememberLauncherForActivityResult
        try {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file.bin"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null && bytes.size <= 28000) {
                val mid = mesh?.sendFile(name, bytes) ?: ""
                messages = msgStore.add(ChatMessage(
                    origin = "me", nick = nickname, isFile = true,
                    filename = name, fileData = bytes, isMine = true,
                    deliveryStatus = "sent", msgId = mid
                ))
            } else {
                Toast.makeText(context, "فایل حداکثر ۲۸KB", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (!results.values.all { it }) {
            Toast.makeText(context, "مجوزها لازم هستند", Toast.LENGTH_SHORT).show()
        }
    }

    fun ensurePermissions(): Boolean {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
            return false
        }
        return true
    }

    fun openPrivateChat(hash: String, nick: String) {
        try {
            val myH = MeshPacket.hashId(myId.ifBlank { "local" })
            mesh?.sendX3DHBundle(hash)
            if (PrivateSessionStore.get(hash) == null) {
                try {
                    PrivateSessionStore.startSymmetric(myH, hash, nick, groupPassword)
                } catch (ex: Exception) {
                    // X3DH required — wait for bundle exchange
                    Toast.makeText(
                        context,
                        ex.message ?: "در انتظار باندل X3DH طرف مقابل…",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            privatePeer = hash
            privatePeerNick = nick
            sessionFp = PrivateSessionStore.get(hash)?.fingerprint ?: ""
            safetyVerified = if (!AppState.requireSafetyNumber) true
            else VerifiedPeers.isVerified(context, hash, sessionFp)
            ChatContacts.save(context, hash, nick)
            chatTab = 1
            val mode = PrivateSessionStore.get(hash)?.let { if (it.x3dh) "X3DH+Ratchet" else "موقت" } ?: "در انتظار X3DH"
            Toast.makeText(context, "چت خصوصی با $nick ($mode)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "خطا", Toast.LENGTH_SHORT).show()
        }
    }









    fun sendPrivateVoiceNote(hash: String, nick: String) {
        try {
            openPrivateChat(hash, nick)
            if (!isRecording) {
                if (voiceManager.startRecording() != null) {
                    isRecording = true
                    Toast.makeText(context, "در حال ضبط ویس خصوصی… دوباره بزنید برای ارسال", Toast.LENGTH_LONG).show()
                }
            } else {
                val f = voiceManager.stopRecording()
                isRecording = false
                if (f != null) {
                    val data = f.readBytes()
                    mesh?.sendPrivateVoice(hash, data, isCall = false)
                    messages = msgStore.add(ChatMessage(
                        origin = "me", nick = "🔒 من", text = "🎤 ویس خصوصی",
                        isVoice = true, audioData = data, isMine = true
                    ))
                    Toast.makeText(context, "ویس خصوصی ارسال شد", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "خطا", Toast.LENGTH_SHORT).show()
        }
    }

    

    fun trimMessages(list: List<ChatMessage>): List<ChatMessage> {
        return msgStore.replaceAll(list)
    }

    fun pushMessage(msg: ChatMessage) {
        messages = msgStore.add(msg)
    }

    fun stopMesh() {
        val m = mesh
        m?.stop()
        MeshModule.unbind(m)
        mesh = null
        try { msgStore.clear() } catch (_: Exception) {}
        messages = emptyList()
        isSetup = false
        status = "قطع از شبکه — رمز ذخیره است؛ اتصال مجدد بزنید"
        voiceManager.release()
        // Keep groupPassword / groupId in fields and AppState for one-tap reconnect
        if (groupPassword.isNotBlank()) {
            AppState.lastGroupPassword = groupPassword
            AppState.lastGroupId = groupId.ifBlank { "default" }
            AppState.lastMeshNickname = nickname
            AppState.save(context)
        }
    }

    fun startMesh() {
        status = "در حال اتصال… " + try { com.ghostmago.app.mesh.MeshHealth.transportSummary(context) } catch (_: Exception) { "" }
        try { mesh?.stop() } catch (_: Exception) {}
        mesh = null
        isSetup = false
        MeshConnectivity.prepareForMesh(context)
        // Manual connect without invite = host for any role
        if (!shouldAutoConnect && AppState.pendingMeshPassword.isBlank()) {
            // keep waitingApproval if already true from QR
        }

        val att = com.ghostmago.app.util.Attestation.scan(context)
        if (AppState.strictAttestation && !AppState.devMode) {
            if (att.rooted) {
                Toast.makeText(context, "دستگاه روت — مسدود", Toast.LENGTH_LONG).show()
                return
            }
        }
        if (!ensurePermissions()) return
        if (groupPassword.length < 4) {
            Toast.makeText(context, "رمز گروه حداقل ۴ کاراکتر", Toast.LENGTH_SHORT).show()
            return
        }
        myId = "user_${System.currentTimeMillis()}"
        AppState.nickname = nickname.ifBlank { "کاربر" }
        AppState.groupId = groupId.ifBlank { "default" }
        AppState.save(context)

        val net = MeshModule.create(
            context = context,
            myId = myId,
            nickname = AppState.nickname,
            groupId = AppState.groupId,
            groupPassword = groupPassword,
            role = myRole,
            hostMode = !waitingApproval,
            onText = { origin, nick, text, hops, eph ->
                messages = msgStore.add(ChatMessage(
                    origin = origin, nick = nick, text = text,
                    hops = hops, ephemeralSec = eph
                ))
            },
            onVoice = { origin, nick, audio, hops ->
                messages = msgStore.add(ChatMessage(
                    origin = origin, nick = nick, isVoice = true,
                    audioData = audio, hops = hops,
                    text = if (nick.contains("🔒")) "🎤 ویس خصوصی" else "🎤 ویس"
                ))

            },
            onFile = { origin, nick, filename, data, hops ->
                messages = msgStore.add(ChatMessage(
                    origin = origin, nick = nick, isFile = true,
                    filename = filename, fileData = data, hops = hops
                ))
            },
            onImage = { origin, nick, jpeg, cap, hops ->
                messages = msgStore.add(ChatMessage(
                    origin = origin, nick = nick,
                    text = cap.ifBlank { "📷 عکس" },
                    hops = hops, imageBytes = jpeg
                ))
            },
            onDelivered = { mid ->
                messages = msgStore.mapUpdate {
                    if (it.msgId == mid) it.copy(deliveryStatus = "delivered") else it
                }
            },
            onDeliveryFailed = { mid ->
                messages = msgStore.mapUpdate {
                    if (it.msgId == mid) it.copy(deliveryStatus = "failed") else it
                }
            },
            onPrivate = { origin, nick, text ->
                messages = msgStore.add(ChatMessage(
                    origin = origin, nick = "🔒 $nick", text = text, isMine = false
                ))
            },
            onPrivHs = { origin, nick, staticB64, ephB64 ->
                try {
                    val myH = MeshPacket.hashId(myId.ifBlank { "local" })
                    val sess = try {
                        if (staticB64.isNotBlank() && ephB64.isNotBlank()) {
                            val ik = android.util.Base64.decode(staticB64, android.util.Base64.NO_WRAP)
                            val eph = android.util.Base64.decode(ephB64, android.util.Base64.NO_WRAP)
                            PrivateSessionStore.startX3DHBob(origin, nick, ik, eph)
                        } else {
                            PrivateSessionStore.startSymmetric(myH, origin, nick, groupPassword)
                        }
                    } catch (_: Exception) {
                        PrivateSessionStore.startSymmetric(myH, origin, nick, groupPassword)
                    }
                    sessionFp = sess.fingerprint
                    privatePeer = origin
                    privatePeerNick = nick
                    safetyVerified = if (!AppState.requireSafetyNumber) true
                    else VerifiedPeers.isVerified(context, origin, sessionFp)
                    Toast.makeText(context, "جلسه خصوصی با $nick", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                }
            },
            onJoinReq = { origin, nick, roleStr ->
                showPending = true
                val n = nick.ifBlank { "کاربر" }
                pendingList = (listOf(origin to n) + pendingList.filter { it.first != origin }).distinctBy { it.first }
                joinReqHash = origin
                joinReqNick = n
                showJoinReqDialog = true
                Toast.makeText(context, "درخواست عضویت از $n — تأیید یا رد کنید", Toast.LENGTH_LONG).show()
            },
            onMemberUpdate = {
                memberCount = mesh?.getMembers()?.size ?: 1
                val fromMesh = mesh?.getPending()?.map { it.first to it.second.nick } ?: emptyList()
                pendingList = (fromMesh + pendingList).distinctBy { it.first }
                if (pendingList.isNotEmpty()) {
                    showPending = true
                    val first = pendingList.first()
                    if (!showJoinReqDialog) {
                        joinReqHash = first.first
                        joinReqNick = first.second
                        showJoinReqDialog = true
                    }
                }
                membersList = mesh?.getMembers()?.map { it.first to it.second.nick } ?: emptyList()
                memberStatuses = mesh?.getMemberStatuses() ?: emptyList()
                onlineCount = memberStatuses.count { it.online }
                // Save peers as contacts so Private tab is never empty after join
                try {
                    for ((h, n) in membersList) {
                        if (n != nickname && h.isNotBlank()) ChatContacts.save(context, h, n)
                    }
                } catch (_: Exception) {}
            },
            onStatus = { if (it != status) status = it },
            onCallSignal = { _, _, _, _ -> },
            onJoinAccepted = { roleName ->
                membershipOk = true
                waitingApproval = false
                status = "عضویت تأیید شد ($roleName)"
                Toast.makeText(context, "فرمانده تأیید کرد — عضو شبکه شدید", Toast.LENGTH_LONG).show()
                ChatContacts.save(context, MeshPacket.hashId(myId), nickname)
            }
        )
        net.start()
        mesh = net
        // Register host group on commander dashboard as operational room
        try {
            if (!waitingApproval) {
                com.ghostmago.app.data.RoomStore.ensureFromActiveMesh(
                    context,
                    AppState.groupId,
                    groupPassword,
                    nickname.ifBlank { "گروه" } + " · " + AppState.groupId
                )
            }
        } catch (_: Exception) {}
        isSetup = true
        AppState.lastGroupPassword = groupPassword
        AppState.lastGroupId = AppState.groupId
        AppState.lastMeshNickname = AppState.nickname
        AppState.groupId = AppState.groupId
        AppState.save(context)
        AuditLog.append(context, "MESH_JOIN", AppState.groupId)
        net.sendJoinRequest()
        if (waitingApproval) {
            Toast.makeText(context, "درخواست عضویت ارسال شد — منتظر تأیید میزبان/فرمانده", Toast.LENGTH_LONG).show()
        } else {
            membershipOk = true
            waitingApproval = false
            status = "میزبان شبکه — آماده تأیید اعضا"
        }
        chatTab = 0
    }

    LaunchedEffect(shouldAutoConnect, groupPassword, groupId) {
        if (shouldAutoConnect && !isSetup && groupPassword.length >= 4) {
            shouldAutoConnect = false
            status = "آمادهسازی وایفای و مجوزها…"
            val missing = MeshConnectivity.missingPermissions(context)
            if (missing.isNotEmpty()) {
                meshPermLauncher.launch(missing)
                delay(600)
            }
            // Retry Wi-Fi up to ~12s so user can turn it on from panel
            var ready = MeshConnectivity.prepareForMesh(context)
            var tries = 0
            while (!MeshConnectivity.isWifiEnabled(context) && tries < 12) {
                status = "وایفای را روشن کنید… ($tries)"
                delay(1000)
                tries++
            }
            ready = MeshConnectivity.isWifiEnabled(context)
            if (!ready) {
                status = "وایفای خاموش است — روشن کنید و دوباره وصل شوید"
                Toast.makeText(context, "وایفای لازم است", Toast.LENGTH_LONG).show()
                // still try start — some devices report false negatives
            }
            delay(500)
            status = "اتصال خودکار به میزبان…"
            startMesh()
        }
    }

    LaunchedEffect(isSetup, mesh) {
        var tick = 0
        while (isSetup && mesh != null) {
            val fromMesh = mesh?.getPending()?.map { it.first to it.second.nick } ?: emptyList()
            if (fromMesh.isNotEmpty()) {
                val merged = (fromMesh + pendingList).distinctBy { it.first }
                if (merged != pendingList) {
                    pendingList = merged
                    showPending = true
                }
                if (!showJoinReqDialog && merged.isNotEmpty()) {
                    val first = merged.first()
                    joinReqHash = first.first
                    joinReqNick = first.second
                    showJoinReqDialog = true
                }
            }
            tick++
            if (tick % 30 == 0) {
                val msg = com.ghostmago.app.data.DeadManSwitch.poll(context)
                    ?: com.ghostmago.app.data.DeadManSwitch.consumePendingBroadcast(context)
                if (msg != null) {
                    try {
                        mesh?.sendBroadcast("⚠ DMS: $msg")
                        Toast.makeText(context, "کلید مرگ فعال شد", Toast.LENGTH_LONG).show()
                    } catch (_: Exception) {
                    }
                }
            }
            // Adaptive poll: fast only when requests pending (saves CPU/UI)
            delay(if (pendingList.isNotEmpty() || showJoinReqDialog) 3500L else 12000L)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(45_000) // rare cleanup — avoid UI jank
            val now = System.currentTimeMillis()
            var filtered = messages.filter { m ->
                val ephOk = m.ephemeralSec <= 0 || (now - m.time) < m.ephemeralSec * 1000L
                val retOk = !RetentionPolicy.isExpired(m.time, m.classification)
                ephOk && retOk
            }
            if (filtered.size > 18) filtered = filtered.takeLast(18)
            filtered = filtered.mapIndexed { idx, m ->
                if (idx < filtered.size - 5 && (m.audioData != null || m.imageBytes != null || m.fileData != null))
                    m.copy(
                        audioData = null,
                        imageBytes = null,
                        fileData = null,
                        text = m.text.ifBlank {
                            when {
                                m.isVoice -> "🎤 ویس"
                                m.imageBytes != null -> "🖼 تصویر"
                                m.isFile -> "📎 فایل"
                                else -> m.text
                            }
                        }
                    )
                else m
            }
            if (filtered.size != messages.size) {
                messages = msgStore.replaceAll(filtered)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { stopMesh() } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("شبکه ارتباطی", color = TextPrimary, fontWeight = FontWeight.Bold)
                        if (isSetup) {
                            Text(status, color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        stopMesh()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                actions = {
                    if (isSetup && canManageJoins) {
                        IconButton(onClick = {
                            val payload = InviteQr.buildPayload(
                                groupId.ifBlank { "default" },
                                groupPassword,
                                nickname.ifBlank { "فرمانده" },
                                "soldier",
                                inviteTtl
                            )
                            inviteBmp = InviteQr.toBitmap(payload, 640)
                            showInviteQr = true
                        }) {
                            Icon(Icons.Default.QrCode, null, tint = Primary)
                        }
                    }
                    if (isSetup) {
                        IconButton(onClick = { stopMesh() }) {
                            Icon(Icons.Default.Stop, null, tint = Danger)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = BgDark
    ) { padding ->
        if (!isSetup) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("راهاندازی شبکه ارتباطی — همه نقشها", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (groupPassword.length >= 4) {
                    Text("رمز گروه ذخیرهشده — برای ورود مجدد فقط «اتصال» را بزنید.", color = Secondary, fontSize = 13.sp)
                }
                Text("هر دو گوشی روی یک وایفای باشند. یک نفر نقش فرمانده داشته باشد.", color = TextSecondary, fontSize = 12.sp)
                Text("پس از اسکن بارکد، اتصال خودکار است؛ نیرو منتظر تأیید فرمانده میماند.", color = TextSecondary, fontSize = 12.sp)
                Text("نقش: ${myRole.symbol} ${myRole.fa}", color = Accent, fontSize = 14.sp)
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("نام مستعار") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = meshFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )
                Text("نقش شما (همه نقشها پشتیبانی میشوند)", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    MeshRole.entries.forEach { r ->
                        FilterChip(
                            selected = AppState.meshRole == r,
                            onClick = {
                                if (r.requiresRolePassword()) {
                                    rolePwDialog = r
                                    rolePwInput = ""
                                } else {
                                    AppState.meshRole = r
                                    AppState.save(context)
                                }
                            },
                            label = { Text("${r.symbol} ${r.fa}", fontSize = 10.sp) }
                        )
                    }
                }
                Text("میزبان: اتصال دستی بدون اسکن. مهمان: اسکن بارکد → انتظار تأیید.", color = TextMuted, fontSize = 11.sp)
                OutlinedTextField(
                    value = groupId,
                    onValueChange = { groupId = it },
                    label = { Text("شناسه گروه") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = meshFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = groupPassword,
                    onValueChange = { groupPassword = it },
                    label = { Text("رمز گروه (حداقل ۴)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = meshFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (myRole.canInvite()) {
                    Text("مدت اعتبار بارکد", color = TextSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(300 to "5m", 900 to "15m", 3600 to "1h", 21600 to "6h", 86400 to "24h").forEach { (sec, label) ->
                            FilterChip(
                                selected = inviteTtl == sec,
                                onClick = { inviteTtl = sec },
                                label = { Text(label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = inviteHoursCustom,
                        onValueChange = { v ->
                            inviteHoursCustom = v.filter { c -> c.isDigit() || c == '.' }
                            val h = inviteHoursCustom.toDoubleOrNull()
                            if (h != null && h > 0) {
                                inviteTtl = (h * 3600).toInt().coerceIn(60, 86400 * 30)
                            }
                        },
                        label = { Text("Custom hours") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = meshFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            if (groupPassword.length < 4) {
                                Toast.makeText(context, "رمز حداقل ۴ کاراکتر", Toast.LENGTH_SHORT).show()
                            } else {
                                val payload = InviteQr.buildPayload(
                                    groupId.ifBlank { "default" },
                                    groupPassword,
                                    nickname.ifBlank { "فرمانده" },
                                    "soldier",
                                    inviteTtl
                                )
                                inviteBmp = InviteQr.toBitmap(payload, 640)
                                showInviteQr = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("ساخت بارکد دعوت") }
                }
                if (showInviteQr && inviteBmp != null) {
                    Image(
                        bitmap = inviteBmp!!.asImageBitmap(),
                        contentDescription = "QR",
                        modifier = Modifier.size(220.dp).align(Alignment.CenterHorizontally)
                    )
                }
                Button(
                    onClick = { inviteScanLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Secondary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("بارکد فرمانده از گالری") }
                Button(
                    onClick = {
                        try {
                            nav?.navigate(com.ghostmago.app.ui.Screen.QrScan.route)
                                ?: Toast.makeText(context, "اسکن از منوی اصلی", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "خطا", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("اسکن بارکد با دوربین") }
                Text("پس از شناسایی بارکد فرمانده، خودکار وارد شبکه می‌شوید.", color = TextSecondary, fontSize = 12.sp)
                Button(
                    onClick = { startMesh() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Wifi, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            waitingApproval -> "اتصال به شبکه ارتباطی (انتظار تأیید)"
                            groupPassword.length >= 4 -> "اتصال مجدد به گروه"
                            else -> "اتصال / ایجاد شبکه ارتباطی"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
            ) {
                if (showInviteQr && inviteBmp != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    ) {
                        Column(
                            Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                bitmap = inviteBmp!!.asImageBitmap(),
                                contentDescription = "QR",
                                modifier = Modifier.size(180.dp)
                            )
                            TextButton(onClick = { showInviteQr = false }) {
                                Text("بستن", color = Danger)
                            }
                        }
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .background(CardDark, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text("● $status", color = Secondary, fontSize = 12.sp, maxLines = 2)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            when {
                                waitingApproval && !membershipOk -> "⏳ منتظر تأیید فرمانده"
                                membershipOk -> "✓ عضو شبکه"
                                else -> "شبکه فعال"
                            },
                            color = if (waitingApproval && !membershipOk) Warning else Secondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "اعضا $memberCount · آنلاین $onlineCount",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }


                if (readOnlyMesh) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "حالت فقط‌خواندنی (نقش نیرو/تازه‌کار) — ارسال گروهی غیرفعال است. چت خصوصی در صورت تأیید Safety مجاز است.",
                            color = Warning,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                // Multi-room switcher
                if (roomsList.isNotEmpty() && AppState.meshRole.canApprove()) {
                    Text("اتاق‌ها", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        roomsList.filter { it.enabled }.forEach { room ->
                            FilterChip(
                                selected = groupId == room.id || AppState.groupId == room.id,
                                onClick = {
                                    RoomStore.activate(context, room)
                                    groupId = room.id
                                    groupPassword = room.password
                                    Toast.makeText(context, "اتاق: ${room.name}", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text(room.name, fontSize = 11.sp) }
                            )
                        }
                    }
                }
                Text(
                    "مسیر انتقال: ${TransportProfile.labelFa()}" +
                        (mesh?.let { " · صف آفلاین: ${it.offlineQueueSize()}" } ?: ""),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                )

                TabRow(selectedTabIndex = chatTab, containerColor = SurfaceDark, contentColor = Primary) {
                    Tab(selected = chatTab == 0, onClick = { chatTab = 0 }, text = { Text("گروه") })
                    Tab(selected = chatTab == 1, onClick = { chatTab = 1 }, text = { Text("خصوصی") })
                }

                if (chatTab == 1) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .heightIn(max = 200.dp)
                    ) {
                        Column(
                            Modifier
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (privatePeer != null) {
                                Text("🔒 با $privatePeerNick", color = Secondary, fontSize = 12.sp)
                                if (sessionFp.isNotEmpty()) {
                                    Text("شماره اعتماد جلسه", color = TextSecondary, fontSize = 11.sp)
                                    Text(sessionFp, color = Primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        if (safetyVerified) "وضعیت: تأیید شد ✓" else "وضعیت: تأیید نشده",
                                        color = if (safetyVerified) Secondary else Danger,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (AppState.requireSafetyNumber && !safetyVerified) {
                                    OutlinedTextField(
                                        value = safetyInput,
                                        onValueChange = { safetyInput = it },
                                        label = { Text("کد طرف مقابل") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = meshFieldColors()
                                    )
                                    Button(onClick = {
                                        val peer = privatePeer
                                        if (peer == null || sessionFp.isBlank()) {
                                            Toast.makeText(context, "نشست خصوصی آماده نیست", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        if (VerifiedPeers.matchesStrict(sessionFp, safetyInput)) {
                                            VerifiedPeers.markVerified(context, peer, sessionFp)
                                            safetyVerified = true
                                            Toast.makeText(context, "Safety Number تأیید و ذخیره شد", Toast.LENGTH_SHORT).show()
                                        } else {
                                            safetyVerified = false
                                            Toast.makeText(
                                                context,
                                                "مطابقت ندارد — هر دو طرف باید عدد کامل را رو در رو مقایسه کنند",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }) { Text("تأیید اجباری Safety Number") }
                                }
                                TextButton(onClick = {
                                    privatePeer?.let { PrivateSessionStore.remove(it) }
                                    privatePeer = null
                                    privatePeerNick = ""
                                    sessionFp = ""
                                    safetyVerified = false
                                }) { Text("پایان جلسه", color = Danger) }
                            } else {
                                                                Text("مخاطبین — پیام و ویس", color = TextSecondary, fontSize = 12.sp)
                                val saved = ChatContacts.list(context)
                                val contactRows = buildList {
                                    saved.forEach { c -> add(c.hash to c.nick) }
                                    membersList.filter { it.second != nickname }.forEach { add(it) }
                                }.distinctBy { it.first }
                                if (contactRows.isEmpty()) {
                                    Text("هنوز مخاطبی نیست. بعد از تأیید عضویت اینجا میآیند.", color = TextMuted, fontSize = 12.sp)
                                }
                                contactRows.forEach { (hash, nick) ->
                                    val online = memberStatuses.any { it.nick == nick && it.online } ||
                                        membersList.any { it.first == hash }
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = CardDark),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(Modifier.padding(10.dp)) {
                                            Text(
                                                "$nick ${if (online) "● آنلاین" else "○"}",
                                                color = if (online) Secondary else TextPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Button(
                                                    onClick = { openPrivateChat(hash, nick) },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                                ) { Text("💬 پیام", fontSize = 12.sp) }
                                                Button(
                                                    onClick = { sendPrivateVoiceNote(hash, nick) },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                                ) { Text(if (isRecording && privatePeer == hash) "⏹ ارسال" else "🎤 ویس", fontSize = 12.sp) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (canManageJoins && isSetup && (pendingList.isNotEmpty() || showPending)) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("درخواستهای عضویت (فرمانده)", color = Warning, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (canManageJoins) {
                                Button(
                                    onClick = {
                                        if (com.ghostmago.app.data.TwoPersonRule.requiresTwoPerson("REMOTE_WIPE") &&
                                            !com.ghostmago.app.data.TwoPersonRule.hasSecondOperator(context)) {
                                            Toast.makeText(context, "قاعده دو نفره فعال است — نفر دوم را در انطباق ثبت کنید", Toast.LENGTH_LONG).show()
                                        } else {
                                            mesh?.sendRemoteWipe("*", "commander")
                                            com.ghostmago.app.data.ImmutableAudit.append(context, "REMOTE_WIPE", "broadcast")
                                        }
                                        Toast.makeText(context, "دستور پاکسازی از راه دور ارسال شد", Toast.LENGTH_LONG).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) { Text("پاکسازی از راه دور همه (خطرناک)", fontSize = 12.sp) }
                            }
                            if (pendingList.isEmpty()) {
                                Text("در انتظار درخواست… وقتی کسی اسکن کند اینجا نام + تأیید/رد میآید.", color = TextSecondary, fontSize = 12.sp)
                            } else {
                                Text("${pendingList.size} درخواست در صف", color = Warning, fontSize = 12.sp)
                            }
                            pendingList.forEach { (hash, nick) ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    Text(nick, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                try {
                                                    mesh?.approveMember(hash, "soldier", nick)
                                                    ChatContacts.save(context, hash, nick)
                                                    pendingList = pendingList.filter { it.first != hash }
                                                    Toast.makeText(context, "$nick تأیید و عضو شد", Toast.LENGTH_LONG).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, e.message ?: "خطا", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.weight(1f).height(48.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Secondary)
                                        ) { Text("✓ تأیید عضویت", fontWeight = FontWeight.Bold) }
                                        Button(
                                            onClick = {
                                                mesh?.rejectMember(hash)
                                                pendingList = pendingList.filter { it.first != hash }
                                                Toast.makeText(context, "$nick رد شد", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f).height(48.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Danger)
                                        ) { Text("✗ رد", fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                    }
                }

                // Group and private are independent streams
                val visible = messages.filter { m ->
                    val isPrivate = m.nick.contains("🔒") || m.text.contains("خصوصی")
                    if (chatTab == 1) isPrivate else !isPrivate
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(visible, key = { it.id }) { msg ->
                        MeshBubble(msg, timeFmt, voiceManager)
                    }
                }

                Surface(color = SurfaceDark, tonalElevation = 4.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { filePicker.launch("*/*") },
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(CardDark)
                        ) {
                            Icon(Icons.Default.AttachFile, null, tint = Primary)
                        }
                        IconButton(
                            onClick = { imagePickLauncher.launch("image/*") },
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(CardDark)
                        ) {
                            Icon(Icons.Default.Image, null, tint = Accent)
                        }
                        IconButton(
                            onClick = {
                                try {
                                    if (isRecording) {
                                        val file = voiceManager.stopRecording()
                                        isRecording = false
                                        if (file == null) return@IconButton
                                        val data = file.readBytes()
                                        if (data.isEmpty()) return@IconButton
                                        if (chatTab == 1 && privatePeer != null) {
                                            val peer = privatePeer!!
                                            scope.launch {
                                                try {
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                        mesh?.sendPrivateVoice(peer, data, isCall = false)
                                                    }
                                                    messages = msgStore.add(ChatMessage(
                                                        origin = "me", nick = "🔒 من",
                                                        text = "🎤 ویس خصوصی",
                                                        isVoice = true, audioData = data, isMine = true
                                                    ))
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, e.message ?: "خطای ویس", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else {
                                            scope.launch {
                                                try {
                                                    val mid = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                        mesh?.sendVoice(data) ?: ""
                                                    }
                                                    messages = msgStore.add(ChatMessage(
                                                        origin = "me", nick = nickname,
                                                        text = "🎤 ویس",
                                                        isVoice = true, audioData = data, isMine = true,
                                                        deliveryStatus = if (mid.isNotEmpty()) "sent" else "failed",
                                                        msgId = mid
                                                    ))
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, e.message ?: "خطای ویس", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    } else {
                                        if (chatTab == 1 && privatePeer == null) {
                                            Toast.makeText(context, "ابتدا مخاطب خصوصی را انتخاب کنید", Toast.LENGTH_SHORT).show()
                                            return@IconButton
                                        }
                                        if (voiceManager.startRecording() != null) {
                                            isRecording = true
                                            Toast.makeText(
                                                context,
                                                if (chatTab == 1) "ضبط ویس خصوصی…" else "ضبط ویس گروهی…",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(context, "خطا در ضبط", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    isRecording = false
                                    Toast.makeText(context, e.message ?: "خطا", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isRecording) Danger else CardDark)
                        ) {
                            Icon(
                                if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                null,
                                tint = if (isRecording) TextPrimary else Secondary
                            )
                        }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("پیام…", color = TextSecondary) },
                            colors = meshFieldColors(),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 3
                        )
                        IconButton(
                            onClick = {
                                val t = inputText.trim()
                                if (t.isEmpty()) return@IconButton
                                if (mesh == null) {
                                    Toast.makeText(context, "ابتدا به شبکه وصل شوید", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                if (chatTab == 0 && readOnlyMesh) {
                                    Toast.makeText(context, "نقش فقط‌خواندنی — ارسال گروهی مجاز نیست", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                if (chatTab == 1 && AppState.requireSafetyNumber && !safetyVerified) {
                                    Toast.makeText(context, "اول Safety Number را تأیید کنید", Toast.LENGTH_LONG).show()
                                    return@IconButton
                                }
                                val peer = privatePeer
                                val tab = chatTab
                                val nickNow = nickname
                                inputText = ""
                                scope.launch {
                                    try {
                                        if (tab == 1 && peer != null) {
                                            if (AppState.requireSafetyNumber && !safetyVerified) {
                                                Toast.makeText(context, "ابتدا Safety Number", Toast.LENGTH_LONG).show()
                                                return@launch
                                            }
                                            if (PrivateSessionStore.get(peer) == null) {
                                                val myH = MeshPacket.hashId(myId.ifBlank { "local" })
                                                PrivateSessionStore.startSymmetric(myH, peer, privatePeerNick, groupPassword)
                                            }
                                            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                mesh?.sendPrivate(peer, t) == true
                                            }
                                            if (ok) {
                                                messages = msgStore.add(ChatMessage(
                                                    origin = "me", nick = "🔒 من", text = t, isMine = true
                                                ))
                                            } else {
                                                Toast.makeText(context, "خطا در ارسال خصوصی", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            val mid = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                mesh?.sendText(t, ephemeralSec = AppState.ephemeralDefaultSec) ?: ""
                                            }
                                            messages = msgStore.add(ChatMessage(
                                                origin = "me", nick = nickNow, text = t,
                                                isMine = true, deliveryStatus = if (mid.isNotEmpty()) "sent" else "failed", msgId = mid
                                            ))
                                            if (mid.isEmpty()) {
                                                Toast.makeText(context, "ارسال نشد — شبکه را بررسی کنید", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "خطا: ${e.message ?: "ارسال"}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(Primary)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = BgDark)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun meshFieldColors() = OutlinedTextFieldDefaults.colors(
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