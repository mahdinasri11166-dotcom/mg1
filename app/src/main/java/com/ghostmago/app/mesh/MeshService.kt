package com.ghostmago.app.mesh

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ghostmago.app.MainActivity

/**
 * Foreground service keeps mesh alive while screen is off.
 * Message notifications live in MeshNotifier.kt (single definition).
 */
class MeshService : Service() {

    companion object {
        const val CHANNEL_ID = "ghostmago_mesh"
        const val NOTIF_ID = 1001
        var isRunning = false
            private set

        fun start(context: Context) {
            val i = Intent(context, MeshService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MeshService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("مشکات — شبکه ارتباطی فعال")
            .setContentText("در حال گوش دادن به پیامهای امن")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        startForeground(NOTIF_ID, notif)
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "شبکه مش", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "وضعیت شبکه ارتباطی مشکات" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}
