package com.ghostmago.app.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Force Persian (fa-IR) locale + RTL layout direction for clear joined Persian text.
 * Does NOT use zero-width / bidi marks in source code (those break the Kotlin compiler).
 */
object LocaleHelper {
    private val FA = Locale("fa", "IR")

    fun wrap(context: Context): Context {
        Locale.setDefault(FA)
        val res = context.resources
        val config = Configuration(res.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(FA))
        } else {
            @Suppress("DEPRECATION")
            config.locale = FA
        }
        config.setLayoutDirection(FA)
        return context.createConfigurationContext(config)
    }

    fun applyToApp(context: Context) {
        Locale.setDefault(FA)
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(FA))
        } else {
            @Suppress("DEPRECATION")
            config.locale = FA
        }
        config.setLayoutDirection(FA)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
