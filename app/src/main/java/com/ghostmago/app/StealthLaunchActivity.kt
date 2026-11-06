package com.ghostmago.app

import android.app.Activity
import android.os.Bundle
import com.ghostmago.app.util.IconHider

/**
 * No-display entry used after secret dial codes on stubborn OEMs.
 */
class StealthLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.getStringExtra("mishkat_action")) {
            "hide" -> IconHider.hide(this)
            "show" -> IconHider.show(this)
            else -> IconHider.openApp(this)
        }
        finish()
    }
}
