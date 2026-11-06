package com.ghostmago.app.mesh

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log

class WifiDirectManager(
    private val context: Context,
    private val onPeers: (List<WifiP2pDevice>) -> Unit,
    private val onConnected: (Boolean, String?) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? =
        manager?.initialize(context, Looper.getMainLooper(), null)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    onStatus(
                        if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) "Wi-Fi Direct روشن"
                        else "Wi-Fi Direct خاموش"
                    )
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    manager?.requestConnectionInfo(channel) { info ->
                        if (info.groupFormed) {
                            val host = info.groupOwnerAddress?.hostAddress
                            onConnected(info.isGroupOwner, host)
                            onStatus("گروه تشکیل شد host=$host")
                        } else {
                            onConnected(false, null)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onStatus("مدیر P2P آماده")
    }

    @SuppressLint("MissingPermission")
    fun discover() {
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = onStatus("در حال جستجو…")
            override fun onFailure(reason: Int) {
                val msg = when (reason) {
                    WifiP2pManager.ERROR -> "خطای داخلی — وایفای را یکبار خاموش/روشن کنید"
                    WifiP2pManager.P2P_UNSUPPORTED -> "این دستگاه Wi-Fi Direct را پشتیبانی نمیکند"
                    WifiP2pManager.BUSY -> "سیستم مشغول است — چند ثانیه بعد دوباره تلاش کنید"
                    else -> "جستجو ناموفق ($reason) — مکان و وایفای را بررسی کنید"
                }
                onStatus(msg)
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun requestPeers() {
        manager?.requestPeers(channel) { list ->
            val devices = list.deviceList?.toList() ?: emptyList()
            onPeers(devices)
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = onStatus("درخواست اتصال ارسال شد")
            override fun onFailure(reason: Int) {
                onStatus(when (reason) {
                    WifiP2pManager.BUSY -> "اتصال: سیستم مشغول — دوباره تلاش کنید"
                    else -> "اتصال ناموفق ($reason)"
                })
            }
        })
    }

    fun stop() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        try {
            manager?.removeGroup(channel, null)
        } catch (_: Exception) {
        }
    }
}
