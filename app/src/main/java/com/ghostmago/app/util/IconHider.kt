package com.ghostmago.app.util

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.ghostmago.app.MainActivity
import com.ghostmago.app.data.AppState

/**
 * Hide/show launcher icon via activity-alias. Dialer: *#*#1234#*#* / *#*#4321#*#*
 */
object IconHider {

    private fun aliasNames(context: Context): List<ComponentName> {
        val pkg = context.packageName
        return listOf(
            ComponentName(pkg, "$pkg.CalculatorAlias"),
            ComponentName(pkg, ".CalculatorAlias"),
            ComponentName(context, "com.ghostmago.app.CalculatorAlias")
        ).distinctBy { it.flattenToString() }
    }

    fun isVisible(context: Context): Boolean {
        val pm = context.packageManager
        return aliasNames(context).any {
            try {
                pm.getComponentEnabledSetting(it) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } catch (_: Exception) {
                true
            }
        }
    }

    private fun setAll(context: Context, enabled: Boolean) {
        val pm = context.packageManager
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        for (cn in aliasNames(context)) {
            try {
                pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
            } catch (_: Exception) {
            }
        }
        // Second pass
        Handler(Looper.getMainLooper()).postDelayed({
            for (cn in aliasNames(context)) {
                try {
                    pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
                } catch (_: Exception) {
                }
            }
        }, 400)
        Handler(Looper.getMainLooper()).postDelayed({
            for (cn in aliasNames(context)) {
                try {
                    pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
                } catch (_: Exception) {
                }
            }
        }, 1200)
    }

    private fun goHome(context: Context) {
        try {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(home)
        } catch (_: Exception) {
        }
    }

    fun hide(context: Context) {
        try {
            setAll(context, false)
            AppState.iconHidden = true
            AppState.save(context)
            Toast.makeText(
                context,
                "آیکون مخفی شد. اگر هنوز هست صفحه را قفل کنید. بازگشت: *#*#4321#*#*",
                Toast.LENGTH_LONG
            ).show()
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    (context as? Activity)?.moveTaskToBack(true)
                } catch (_: Exception) {
                }
                goHome(context)
            }, 300)
        } catch (e: Exception) {
            Toast.makeText(context, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun show(context: Context) {
        try {
            setAll(context, true)
            AppState.iconHidden = false
            AppState.save(context)
            Toast.makeText(context, "آیکون فعال شد", Toast.LENGTH_LONG).show()
            openApp(context)
        } catch (e: Exception) {
            Toast.makeText(context, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun openApp(context: Context) {
        try {
            val i = Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
            }
            context.startActivity(i)
        } catch (_: Exception) {
        }
    }

    fun setVisible(context: Context, visible: Boolean) {
        if (visible) show(context) else hide(context)
    }
}
