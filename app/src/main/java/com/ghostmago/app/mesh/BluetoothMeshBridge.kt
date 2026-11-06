package com.ghostmago.app.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.ghostmago.app.data.AppState
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

/**
 * Classic Bluetooth RFCOMM fallback when Wi-Fi mesh is unavailable.
 * Enable in Settings. Requires BLUETOOTH_CONNECT on API 31+.
 */
class BluetoothMeshBridge(
    private val context: Context,
    private val onPacket: (ByteArray) -> Unit,
    private val onStatus: (String) -> Unit
) {
    companion object {
        private val UUID_SPP = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
        private const val NAME = "MishkatMesh"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverJob: Job? = null
    private var clientJob: Job? = null
    private val peers = mutableListOf<BluetoothSocket>()

    @SuppressLint("MissingPermission")
    fun start() {
        if (!AppState.useBluetoothMesh) return
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            onStatus("بلوتوث خاموش است")
            return
        }
        stop()
        serverJob = scope.launch {
            var server: BluetoothServerSocket? = null
            try {
                server = adapter.listenUsingInsecureRfcommWithServiceRecord(NAME, UUID_SPP)
                onStatus("بلوتوث: در انتظار اتصال")
                while (isActive && AppState.useBluetoothMesh) {
                    val sock = server.accept() ?: continue
                    synchronized(peers) { peers.add(sock) }
                    onStatus("بلوتوث: همسال متصل شد")
                    launch { readLoop(sock) }
                }
            } catch (e: Exception) {
                Log.w("BtMesh", "server", e)
            } finally {
                try { server?.close() } catch (_: Exception) {}
            }
        }
        // Try bonded devices
        clientJob = scope.launch {
            try {
                for (dev in adapter.bondedDevices) {
                    if (!AppState.useBluetoothMesh) break
                    try {
                        val sock = dev.createInsecureRfcommSocketToServiceRecord(UUID_SPP)
                        sock.connect()
                        synchronized(peers) { peers.add(sock) }
                        onStatus("بلوتوث به ${dev.name} وصل شد")
                        launch { readLoop(sock) }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.w("BtMesh", "client", e)
            }
        }
    }

    private fun readLoop(sock: BluetoothSocket) {
        try {
            val input = DataInputStream(sock.inputStream)
            while (sock.isConnected) {
                val len = input.readInt()
                if (len <= 0 || len > 70000) break
                val buf = ByteArray(len)
                input.readFully(buf)
                onPacket(buf)
            }
        } catch (_: Exception) {
        } finally {
            try { sock.close() } catch (_: Exception) {}
            synchronized(peers) { peers.remove(sock) }
        }
    }

    fun send(packet: ByteArray) {
        if (!AppState.useBluetoothMesh) return
        synchronized(peers) {
            for (s in peers.toList()) {
                try {
                    val out = DataOutputStream(s.outputStream)
                    out.writeInt(packet.size)
                    out.write(packet)
                    out.flush()
                } catch (_: Exception) {
                    try { s.close() } catch (_: Exception) {}
                    peers.remove(s)
                }
            }
        }
    }

    fun stop() {
        serverJob?.cancel()
        clientJob?.cancel()
        synchronized(peers) {
            peers.forEach { try { it.close() } catch (_: Exception) {} }
            peers.clear()
        }
    }
}
