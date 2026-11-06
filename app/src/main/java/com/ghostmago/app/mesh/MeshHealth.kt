package com.ghostmago.app.mesh

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import com.ghostmago.app.data.AppState

/**
 * Lightweight health helpers for مشکات mesh — battery-aware intervals
 * and human-readable transport summary (no heavy scans).
 */
object MeshHealth {

    fun batteryPercent(context: Context): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        } catch (_: Exception) {
            100
        }
    }

    fun isCharging(context: Context): Boolean {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            if (Build.VERSION.SDK_INT >= 26) {
                bm.isCharging
            } else {
                val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val battery = context.registerReceiver(null, ifilter) ?: return false
                val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Beacon delay ms — longer on low battery to reduce radio wakeups. */
    fun beaconIntervalMs(context: Context): Long {
        val pct = batteryPercent(context)
        val base = 15_000L
        return when {
            pct <= 15 && !isCharging(context) -> base * 3
            pct <= 30 && !isCharging(context) -> base * 2
            else -> base
        }
    }

    fun isWifiEnabled(context: Context): Boolean {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.isWifiEnabled
        } catch (_: Exception) {
            false
        }
    }

    fun transportSummary(context: Context): String {
        val parts = mutableListOf<String>()
        parts.add(if (isWifiEnabled(context)) "وای‌فای✓" else "وای‌فای✗")
        parts.add(if (AppState.useBluetoothMesh) "بلوتوث✓" else "بلوتوث–")
        parts.add(
            when {
                !AppState.useUsbRadioMesh -> "رادیو–"
                else -> "رادیوUSB(${if (AppState.usbRadioBand == "am") "AM" else "FM"})✓"
            }
        )
        if (AppState.relayEnabled) parts.add("رله✓")
        if (AppState.radioSilence) parts.add("سکوت")
        return parts.joinToString(" · ")
    }
}
