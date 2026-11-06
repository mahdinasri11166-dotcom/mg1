package com.ghostmago.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ghostmago.app.StealthLaunchActivity
import com.ghostmago.app.data.AppState

class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "android.provider.Telephony.SECRET_CODE") return
        val host = intent.data?.host?.trim().orEmpty()
        if (host.isEmpty()) return
        try {
            AppState.load(context)
        } catch (_: Exception) {
        }
        val hideCode = AppState.hideIconCode.ifBlank { "1234" }
        val showCode = AppState.showIconCode.ifBlank { "4321" }

        val action = when (host) {
            hideCode, "1234" -> "hide"
            showCode, "4321" -> "show"
            else -> return
        }
        try {
            val i = Intent(context, StealthLaunchActivity::class.java).apply {
                putExtra("mishkat_action", action)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
        } catch (_: Exception) {
            if (action == "hide") IconHider.hide(context) else IconHider.show(context)
        }
    }
}
