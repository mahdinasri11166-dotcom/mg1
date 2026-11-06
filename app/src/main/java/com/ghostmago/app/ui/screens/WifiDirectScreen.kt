package com.ghostmago.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ghostmago.app.mesh.WifiDirectManager
import com.ghostmago.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiDirectScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var peers by remember { mutableStateOf<List<android.net.wifi.p2p.WifiP2pDevice>>(emptyList()) }
    var status by remember { mutableStateOf("آماده") }
    var manager by remember { mutableStateOf<WifiDirectManager?>(null) }

    fun startMgr() {
        manager?.stop()
        val m = WifiDirectManager(
            context = context,
            onPeers = { peers = it },
            onStatus = { status = it },
            onConnected = { owner, host -> status = if (host != null) "متصل GO=$owner host=$host" else "قطع" }
        )
        m.start()
        manager = m
        m.discover()
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startMgr()
        } else {
            Toast.makeText(context, "مجوزهای Wi-Fi Direct لازم است", Toast.LENGTH_LONG).show()
        }
    }

    fun requestAndStart() {
        val needed = mutableListOf<String>().apply {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_WIFI_STATE)
            add(Manifest.permission.CHANGE_WIFI_STATE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) startMgr()
        else permLauncher.launch(needed.toTypedArray())
    }

    DisposableEffect(Unit) {
        onDispose { manager?.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wi-Fi Direct", color = TextPrimary) },
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
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(status, color = Secondary, fontSize = 13.sp)
            Button(
                onClick = { requestAndStart() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
            ) { Text("جستجوی دستگاهها", fontWeight = FontWeight.Bold) }

            Text("همسایهها", color = TextPrimary, fontWeight = FontWeight.Bold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(peers, key = { it.deviceAddress }) { d ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(d.deviceName ?: "?", color = TextPrimary)
                                Text(d.deviceAddress, color = TextSecondary, fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                manager?.connect(d)
                                Toast.makeText(context, "در حال اتصال…", Toast.LENGTH_SHORT).show()
                            }) { Text("اتصال", color = Accent) }
                        }
                    }
                }
            }
        }
    }
}
