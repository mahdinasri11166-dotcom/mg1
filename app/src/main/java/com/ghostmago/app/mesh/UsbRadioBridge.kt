package com.ghostmago.app.mesh

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.ghostmago.app.data.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * External FM/AM (or any packet radio) dongle on USB-C / OTG.
 *
 * Phones do not expose native FM/AM transmitters to apps.
 * A soldered or commercial module must appear as a USB device
 * (typically USB-Serial / CDC / bulk endpoints). This bridge:
 *  - discovers USB host devices
 *  - requests permission
 *  - frames mesh packets over bulk IN/OUT
 *
 * Framing: AA 55 | len_hi len_lo | payload | checksum(xor of payload)
 * Max payload ~512 bytes preferred for radio; larger packets are skipped
 * when [AppState.usbRadioTextOnly] is true.
 */
class UsbRadioBridge(
    private val context: Context,
    private val onPacket: (ByteArray) -> Unit,
    private val onStatus: (String) -> Unit
) {
    companion object {
        private const val TAG = "UsbRadio"
        private const val ACTION_USB_PERMISSION = "com.ghostmago.app.USB_RADIO_PERMISSION"
        private const val MAX_RADIO_PAYLOAD = 480
        private const val HDR0 = 0xAA.toByte()
        private const val HDR1 = 0x55.toByte()
    }

    private val usb = context.applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readJob: Job? = null
    private var conn: UsbDeviceConnection? = null
    private var usbIface: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null
    private val outQueue = ConcurrentLinkedQueue<ByteArray>()
    @Volatile private var running = false
    private var lastDeviceName: String = ""

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted && device != null) {
                scope.launch { openDevice(device) }
            } else {
                onStatus("مجوز USB رادیو رد شد")
            }
        }
    }

    fun start() {
        if (!AppState.useUsbRadioMesh) return
        val band = AppState.usbRadioBand.lowercase()
        val allowed = AppState.allowedUsbRadioBands.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        if (allowed.isNotEmpty() && band !in allowed) {
            onStatus("باند رادیو $band در فهرست مجاز سازمان نیست")
            return
        }
        if (running) return
        running = true
        try {
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(permissionReceiver, filter)
            }
        } catch (_: Exception) {
        }
        scope.launch {
            onStatus("رادیو USB: جستجوی ماژول…")
            while (running && AppState.useUsbRadioMesh && isActive) {
                try {
                    if (conn == null) {
                        scanAndConnect()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "scan", e)
                }
                delay(if (conn != null) 8000 else 4000)
            }
        }
        scope.launch {
            while (running && AppState.useUsbRadioMesh && isActive) {
                try {
                    val p = outQueue.poll()
                    if (p != null) writeFrame(p)
                    else delay(40)
                } catch (_: Exception) {
                    delay(100)
                }
            }
        }
    }

    fun stop() {
        running = false
        try {
            context.unregisterReceiver(permissionReceiver)
        } catch (_: Exception) {
        }
        closeDevice()
        scope.cancel()
        onStatus("رادیو USB: خاموش")
    }

    fun send(packet: ByteArray) {
        if (!running || !AppState.useUsbRadioMesh) return
        if (packet.size > MAX_RADIO_PAYLOAD) {
            // Prefer short control/text; skip huge media on narrowband radio
            if (AppState.usbRadioTextOnly) return
            if (packet.size > 1200) return
        }
        outQueue.offer(packet)
        while (outQueue.size > 20) outQueue.poll()
    }

    fun isConnected(): Boolean = conn != null && epOut != null

    fun deviceLabel(): String = lastDeviceName.ifBlank { "—" }

    private fun scanAndConnect() {
        val list = usb.deviceList?.values ?: emptyList()
        if (list.isEmpty()) {
            onStatus("رادیو USB: ماژولی پیدا نشد — OTG و ماژول را وصل کنید")
            return
        }
        for (dev in list) {
            lastDeviceName = "${dev.deviceName} vid=${dev.vendorId} pid=${dev.productId}"
            if (usb.hasPermission(dev)) {
                openDevice(dev)
                return
            }
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION), flags
            )
            usb.requestPermission(dev, pi)
            onStatus("رادیو USB: درخواست مجوز برای $lastDeviceName")
            return
        }
    }

    private fun openDevice(device: UsbDevice) {
        closeDevice()
        val connection = usb.openDevice(device) ?: run {
            onStatus("رادیو USB: باز نشد")
            return
        }
        // Prefer CDC data interface or any with bulk IN+OUT
        var chosen: UsbInterface? = null
        var inEp: UsbEndpoint? = null
        var outEp: UsbEndpoint? = null
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            var a: UsbEndpoint? = null
            var b: UsbEndpoint? = null
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (ep.direction == UsbConstants.USB_DIR_IN) a = ep
                if (ep.direction == UsbConstants.USB_DIR_OUT) b = ep
            }
            if (a != null && b != null) {
                chosen = intf
                inEp = a
                outEp = b
                break
            }
        }
        if (chosen == null || inEp == null || outEp == null) {
            connection.close()
            onStatus("رادیو USB: endpoint مناسب (Bulk) ندارد — ماژول سریال/CDC لازم است")
            return
        }
        if (!connection.claimInterface(chosen, true)) {
            connection.close()
            onStatus("رادیو USB: claim ناموفق")
            return
        }
        conn = connection
        usbIface = chosen
        epIn = inEp
        epOut = outEp
        val band = if (AppState.usbRadioBand == "am") "AM" else "FM"
        onStatus("رادیو USB متصل ($band): $lastDeviceName")
        readJob?.cancel()
        readJob = scope.launch { readLoop() }
    }

    private fun closeDevice() {
        try {
            readJob?.cancel()
            readJob = null
            usbIface?.let { conn?.releaseInterface(it) }
            conn?.close()
        } catch (_: Exception) {
        }
        conn = null
        usbIface = null
        epIn = null
        epOut = null
    }

    private fun writeFrame(payload: ByteArray) {
        val c = conn ?: return
        val out = epOut ?: return
        val len = payload.size.coerceAtMost(MAX_RADIO_PAYLOAD)
        val body = if (payload.size > len) payload.copyOf(len) else payload
        var xor = 0
        for (b in body) xor = xor xor (b.toInt() and 0xFF)
        val frame = ByteArray(4 + body.size + 1)
        frame[0] = HDR0
        frame[1] = HDR1
        frame[2] = ((body.size shr 8) and 0xFF).toByte()
        frame[3] = (body.size and 0xFF).toByte()
        System.arraycopy(body, 0, frame, 4, body.size)
        frame[4 + body.size] = xor.toByte()
        try {
            c.bulkTransfer(out, frame, frame.size, 800)
        } catch (e: Exception) {
            Log.w(TAG, "write", e)
        }
    }

    private suspend fun readLoop() {
        val c = conn ?: return
        val inp = epIn ?: return
        val buf = ByteArray(512)
        val acc = ArrayList<Byte>(1024)
        // Use a local CoroutineScope from the current context to safely access isActive
        while (running && conn != null && coroutineContext.isActive) {
            try {
                val n = c.bulkTransfer(inp, buf, buf.size, 500)
                if (n == null || n <= 0) {
                    delay(20)
                    continue
                }
                for (i in 0 until n) acc.add(buf[i])
                // parse frames
                while (acc.size >= 5) {
                    // find header
                    var hi = -1
                    for (i in 0 until acc.size - 1) {
                        if (acc[i] == HDR0 && acc[i + 1] == HDR1) {
                            hi = i
                            break
                        }
                    }
                    if (hi < 0) {
                        acc.clear()
                        break
                    }
                    if (hi > 0) {
                        repeat(hi) { acc.removeAt(0) }
                    }
                    if (acc.size < 5) break
                    val len = ((acc[2].toInt() and 0xFF) shl 8) or (acc[3].toInt() and 0xFF)
                    if (len <= 0 || len > MAX_RADIO_PAYLOAD) {
                        acc.removeAt(0)
                        continue
                    }
                    if (acc.size < 4 + len + 1) break
                    val payload = ByteArray(len)
                    var xor = 0
                    for (i in 0 until len) {
                        payload[i] = acc[4 + i]
                        xor = xor xor (payload[i].toInt() and 0xFF)
                    }
                    val chk = acc[4 + len].toInt() and 0xFF
                    // drop frame bytes
                    repeat(4 + len + 1) { if (acc.isNotEmpty()) acc.removeAt(0) }
                    if (chk == xor) {
                        try {
                            onPacket(payload)
                        } catch (_: Exception) {
                        }
                    }
                }
                if (acc.size > 4096) acc.clear()
            } catch (_: Exception) {
                delay(100)
            }
        }
    }
}
