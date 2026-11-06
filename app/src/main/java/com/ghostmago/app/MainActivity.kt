package com.ghostmago.app

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.ghostmago.app.data.AppState
import com.ghostmago.app.ui.NavGraph
import com.ghostmago.app.ui.theme.BgDark
import com.ghostmago.app.ui.theme.GhostMagoTheme
import com.ghostmago.app.util.LocaleHelper

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // First frame ASAP — no edge-to-edge / secure flag before setContent
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                GhostMagoTheme(darkTheme = true) {
                    Surface(modifier = Modifier.fillMaxSize(), color = BgDark) {
                        NavGraph()
                    }
                }
            }
        }

        // Defer non-critical window + background work after first paint
        Handler(Looper.getMainLooper()).post {
            try {
                LocaleHelper.applyToApp(this)
            } catch (_: Exception) {
            }
            try {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
                window.decorView.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
            } catch (_: Exception) {
            }
        }

        Thread({
            try {
                com.ghostmago.app.util.ApkCleanup.runOnce(applicationContext)
            } catch (_: Exception) {
            }
            try {
                // Hardware keystore only if already loaded preference says so
                if (AppState.isReady && AppState.useHardwareKeystore) {
                    com.ghostmago.app.crypto.HardwareKeystore.ensureKey(true)
                }
            } catch (_: Exception) {
            }
            try {
                if (AppState.isReady) {
                    com.ghostmago.app.data.DeadManSwitch.checkIn(applicationContext)
                }
            } catch (_: Exception) {
            }
        }, "main-bg-init").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
    }
}
