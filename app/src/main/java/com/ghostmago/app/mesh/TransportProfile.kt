package com.ghostmago.app.mesh

import com.ghostmago.app.data.AppState

/**
 * Which physical path مشکات may use for mesh packets.
 * auto = Wi-Fi + optional BT + optional USB radio
 */
object TransportProfile {
    const val AUTO = "auto"
    const val WIFI = "wifi"
    const val BT = "bt"
    const val USB = "usb"

    fun mode(): String = AppState.transportMode.ifBlank { AUTO }

    fun allowWifi(): Boolean = mode() == AUTO || mode() == WIFI

    fun allowBluetooth(): Boolean =
        (mode() == AUTO || mode() == BT) && AppState.useBluetoothMesh

    fun allowUsbRadio(): Boolean =
        (mode() == AUTO || mode() == USB) && AppState.useUsbRadioMesh

    fun labelFa(): String = when (mode()) {
        WIFI -> "فقط وای‌فای"
        BT -> "اولویت بلوتوث"
        USB -> "اولویت رادیو USB"
        else -> "خودکار (وای‌فای→بلوتوث→رادیو)"
    }
}
