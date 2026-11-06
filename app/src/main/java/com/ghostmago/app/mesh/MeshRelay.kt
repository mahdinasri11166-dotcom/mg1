package com.ghostmago.app.mesh

import android.util.Base64
import android.util.Log
import com.ghostmago.app.data.AppState
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Optional TCP internet relay so distant peers share packets.
 * Protocol: each line is base64(packet) + newline. Server is simple TCP broadcast hub.
 * User sets host:port in Settings (e.g. relay.example.com:7777).
 */
class MeshRelay(
    private val onPacket: (ByteArray) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private var job: Job? = null
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        if (!AppState.relayEnabled) return
        if (AppState.orgBlockInternetRelay) {
            onStatus("رله اینترنت توسط سیاست سازمانی مسدود است")
            return
        }
        val host = AppState.relayHost.substringBefore(":").ifBlank { return }
        val port = AppState.relayHost.substringAfter(":", "7777").toIntOrNull() ?: 7777
        job?.cancel()
        job = scope.launch {
            while (isActive && AppState.relayEnabled) {
                try {
                    onStatus("اتصال رله اینترنت…")
                    val s = Socket()
                    s.connect(InetSocketAddress(host, port), 8000)
                    s.soTimeout = 15000
                    socket = s
                    writer = PrintWriter(s.getOutputStream(), true)
                    onStatus("رله اینترنت متصل ($host:$port)")
                    val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                    while (isActive && AppState.relayEnabled) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        try {
                            val data = Base64.decode(line.trim(), Base64.NO_WRAP)
                            if (data.isNotEmpty()) onPacket(data)
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    onStatus("رله قطع شد: ${e.message?.take(40)}")
                    Log.w("MeshRelay", "relay error", e)
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                    socket = null
                    writer = null
                }
                delay(4000)
            }
        }
    }

    fun send(packet: ByteArray) {
        if (!AppState.relayEnabled) return
        try {
            val line = Base64.encodeToString(packet, Base64.NO_WRAP)
            writer?.println(line)
        } catch (_: Exception) {}
    }

    fun stop() {
        job?.cancel()
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        writer = null
    }
}
