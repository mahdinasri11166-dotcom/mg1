package com.ghostmago.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.navigation.NavController
import com.ghostmago.app.crypto.CryptoEngine
import com.ghostmago.app.data.AppState
import com.ghostmago.app.mesh.InviteQr
import com.ghostmago.app.stego.StegoEngine
import com.ghostmago.app.ui.Screen
import com.ghostmago.app.ui.theme.Accent
import com.ghostmago.app.ui.theme.BgDark
import com.ghostmago.app.ui.theme.Border
import com.ghostmago.app.ui.theme.Primary
import com.ghostmago.app.ui.theme.Secondary
import com.ghostmago.app.ui.theme.SurfaceDark
import com.ghostmago.app.ui.theme.TextPrimary
import com.ghostmago.app.ui.theme.TextSecondary
import com.ghostmago.app.util.MeshConnectivity
import com.ghostmago.app.util.QrHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.common.InvertedLuminanceSource
import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(nav: NavController? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCam by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var scanned by remember { mutableStateOf<String?>(null) }
    var decoded by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useCamera by remember { mutableStateOf(true) }
    val scanning = remember { AtomicBoolean(true) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCam = granted
        if (!granted) {
            Toast.makeText(context, "مجوز دوربین لازم است", Toast.LENGTH_SHORT).show()
        }
    }

    val meshPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    fun handlePayload(raw: String) {
        if (raw.isBlank()) return
        scanned = raw
        // Organizational signed invite token
        try {
            val tok = com.ghostmago.app.orgtrust.OrgTrust.InviteToken.parseQr(raw)
            if (tok != null) {
                val err = com.ghostmago.app.orgtrust.OrgTrust.verifyInvite(context, tok)
                if (err != null) {
                    Toast.makeText(context, "دعوت سازمانی: $err", Toast.LENGTH_LONG).show()
                } else {
                    com.ghostmago.app.orgtrust.OrgTrust.savePendingInvite(
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
                    AppState.groupId = tok.groupId
                    AppState.pendingMeshAutoJoin = true
                    AppState.save(context)
                    decoded = "دعوت سازمانی معتبر
گروه: ${tok.groupId}
نقش: ${tok.role}"
                    Toast.makeText(context, "توکن سازمانی ذخیره شد — به شبکه بروید", Toast.LENGTH_LONG).show()
                    try {
                        nav?.navigate(Screen.Mesh.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    } catch (_: Exception) {}
                    return
                }
            }
        } catch (_: Exception) {}
        val inv = InviteQr.parse(raw)
        if (inv != null) {
            AppState.groupId = inv.groupId
            AppState.pendingMeshPassword = inv.groupPassword
            AppState.pendingMeshAutoJoin = true
            AppState.save(context)
            decoded = "دعوت از ${inv.commanderNick}\nگروه: ${inv.groupId}\nاتصال خودکار…"
            try {
                MeshConnectivity.prepareForMesh(context)
            } catch (_: Exception) {
            }
            val missing = try {
                MeshConnectivity.missingPermissions(context)
            } catch (_: Exception) {
                emptyArray()
            }
            if (missing.isNotEmpty()) {
                try {
                    meshPermLauncher.launch(missing)
                } catch (_: Exception) {
                }
            }
            Toast.makeText(context, "بارکد فرمانده شناسایی شد — ورود به گروه…", Toast.LENGTH_LONG).show()
            try {
                nav?.navigate(Screen.Mesh.route) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            } catch (_: Exception) {
                Toast.makeText(context, "به صفحه شبکه بروید و اتصال را بزنید", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (StegoEngine.looksLikeStego(raw) || raw.contains("GM:")) {
            decoded = "پیام پنهان‌نگاری تشخیص داده شد — رمز را وارد کنید"
            return
        }
        decoded = "محتوای بارکد خوانده شد."
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            @Suppress("DEPRECATION")
            val bmp = android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            val raw = QrHelper.decode(bmp)
            if (raw.isNullOrBlank()) {
                Toast.makeText(context, "بارکد در تصویر پیدا نشد", Toast.LENGTH_SHORT).show()
            } else {
                handlePayload(raw)
            }
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "خطا", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCam) {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اسکن بارکد", color = TextPrimary) },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = useCamera,
                    onClick = { useCamera = true },
                    label = { Text("دوربین") }
                )
                FilterChip(
                    selected = !useCamera,
                    onClick = { useCamera = false },
                    label = { Text("گالری") }
                )
            }

            if (!useCamera) {
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Secondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, null)
                    Text("  انتخاب از گالری")
                }
            }

            if (useCamera) {
                if (!hasCam) {
                    Text("بدون مجوز CAMERA اسکن زنده کار نمی‌کند.", color = TextSecondary, fontSize = 12.sp)
                    Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("درخواست مجوز دوربین")
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val future = ProcessCameraProvider.getInstance(ctx)
                            future.addListener({
                                try {
                                    val provider = future.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val analysis = ImageAnalysis.Builder()
                                        .setTargetResolution(Size(1280, 720))
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                    val reader = MultiFormatReader().apply {
                                        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
                                        hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(
                                            BarcodeFormat.QR_CODE,
                                            BarcodeFormat.AZTEC,
                                            BarcodeFormat.DATA_MATRIX
                                        )
                                        hints[DecodeHintType.TRY_HARDER] = true
                                        hints[DecodeHintType.CHARACTER_SET] = "UTF-8"
                                        setHints(hints)
                                    }
                                    analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                        if (!scanning.get()) {
                                            imageProxy.close()
                                            return@setAnalyzer
                                        }
                                        try {
                                            val text = decodeImageProxy(imageProxy, reader)
                                            if (!text.isNullOrBlank()) {
                                                scanning.set(false)
                                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                    try {
                                                        handlePayload(text)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(
                                                            ctx,
                                                            e.message ?: "خطا",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        scanning.set(true)
                                                    }
                                                }
                                            }
                                        } catch (_: Exception) {
                                        } finally {
                                            imageProxy.close()
                                        }
                                    }
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        analysis
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "دوربین: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                    )
                    Text(
                        "بارکد فرمانده را روبه‌روی دوربین بگیرید — به‌محض شناسایی وارد گروه می‌شوید.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            if (scanned != null && InviteQr.parse(scanned!!) == null) {
                Text("محتوا دریافت شد", color = TextPrimary)
                if (decoded.isNotBlank()) Text(decoded, color = Secondary, fontSize = 13.sp)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("رمز (برای پنهان‌نگاری)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Button(
                    onClick = {
                        try {
                            val raw = scanned ?: return@Button
                            when {
                                StegoEngine.looksLikeStego(raw) || raw.contains("GM:") -> {
                                    if (password.isBlank()) {
                                        Toast.makeText(context, "رمز لازم است", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    decoded = StegoEngine.decode(raw, password)
                                    Toast.makeText(context, "رمزگشایی شد", Toast.LENGTH_SHORT).show()
                                }
                                password.isNotBlank() -> {
                                    decoded = CryptoEngine.decryptString(raw, password)
                                }
                                else -> decoded = raw
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "خطا", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("رمزگشایی / پردازش") }
                TextButton(onClick = {
                    scanned = null
                    decoded = ""
                    password = ""
                    scanning.set(true)
                }) { Text("اسکن دوباره") }
            }
        }
    }
}

/** Multi-attempt QR decode from CameraX YUV frame. */
private fun decodeImageProxy(imageProxy: ImageProxy, reader: MultiFormatReader): String? {
    val width = imageProxy.width
    val height = imageProxy.height
    if (width <= 0 || height <= 0) return null
    val yBuffer = imageProxy.planes[0].buffer
    val ySize = yBuffer.remaining()
    val data = ByteArray(ySize)
    yBuffer.get(data)
    val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
    fun tryDecode(src: com.google.zxing.LuminanceSource): Result? {
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(src)))
        } catch (_: Exception) {
            try {
                reader.reset()
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(InvertedLuminanceSource(src))))
            } catch (_: Exception) {
                null
            } finally {
                reader.reset()
            }
        }
    }
    tryDecode(source)?.text?.let { if (it.isNotBlank()) return it }
    // Center crop for better focus on QR
    val cw = (width * 0.7).toInt().coerceAtLeast(100)
    val ch = (height * 0.7).toInt().coerceAtLeast(100)
    val left = ((width - cw) / 2).coerceAtLeast(0)
    val top = ((height - ch) / 2).coerceAtLeast(0)
    return try {
        val crop = PlanarYUVLuminanceSource(data, width, height, left, top, cw, ch, false)
        tryDecode(crop)?.text
    } catch (_: Exception) {
        null
    }
}
