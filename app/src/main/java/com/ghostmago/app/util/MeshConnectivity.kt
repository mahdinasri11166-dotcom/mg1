package com.ghostmago.app.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Prepares Wi-Fi / location so mesh can bind and discover peers after QR scan.
 */
object MeshConnectivity {

    fun isWifiEnabled(context: Context): Boolean {
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifi.isWifiEnabled
        } catch (_: Exception) {
            true
        }
    }

    fun isLocationEnabled(context: Context): Boolean {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            true
        }
    }

    /** Permissions needed for mesh / Wi-Fi Direct style discovery on modern Android. */
    fun missingPermissions(context: Context): Array<String> {
        val need = mutableListOf<String>()
        val check = { p: String ->
            ContextCompat.checkSelfPermission(context, p) != PackageManager.PERMISSION_GRANTED
        }
        if (check(Manifest.permission.ACCESS_FINE_LOCATION)) need += Manifest.permission.ACCESS_FINE_LOCATION
        if (check(Manifest.permission.ACCESS_COARSE_LOCATION)) need += Manifest.permission.ACCESS_COARSE_LOCATION
        if (Build.VERSION.SDK_INT >= 33) {
            if (check(Manifest.permission.NEARBY_WIFI_DEVICES)) need += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        if (Build.VERSION.SDK_INT >= 31) {
            // optional BT mesh
            if (check(Manifest.permission.BLUETOOTH_CONNECT)) need += Manifest.permission.BLUETOOTH_CONNECT
        }
        return need.toTypedArray()
    }

    /**
     * Try enable Wi-Fi; on Android 10+ opens system Wi-Fi panel.
     * @return true if Wi-Fi already on
     */
    fun ensureWifiOn(context: Context): Boolean {
        if (isWifiEnabled(context)) return true
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val panel = Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(panel)
                Toast.makeText(context, "وایفای را روشن کنید تا به شبکه وصل شوید", Toast.LENGTH_LONG).show()
            } else {
                @Suppress("DEPRECATION")
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifi.isWifiEnabled = true
                Toast.makeText(context, "در حال روشن کردن وایفای…", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                Toast.makeText(context, "وایفای را روشن کنید", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
            }
        }
        return false
    }

    fun ensureLocationOn(context: Context): Boolean {
        if (isLocationEnabled(context)) return true
        try {
            context.startActivity(
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Toast.makeText(
                context,
                "موقعیت مکانی را روشن کنید (برای کشف شبکه لازم است)",
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
        }
        return false
    }

    /**
     * Opens required panels if off. Returns true when ready enough to start mesh.
     */
    fun prepareForMesh(context: Context): Boolean {
        val wifiOk = ensureWifiOn(context)
        // Location often required for Wi-Fi peer discovery on Android 6+
        val locOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ensureLocationOn(context)
        } else true
        return wifiOk && locOk
    }
}
