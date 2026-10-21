package com.example.gymtracker.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.gymtracker.MainActivity

class WorkoutNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "workout_timer_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_OR_UPDATE = "ACTION_START_OR_UPDATE"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESET_TIMER = "ACTION_RESET_TIMER"

        const val EXTRA_CRONOMETRO_STR = "EXTRA_CRONOMETRO_STR"
        const val EXTRA_TIMER_STR = "EXTRA_TIMER_STR"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val cronometroStr = intent?.getStringExtra(EXTRA_CRONOMETRO_STR) ?: "00:00"
        val timerStr = intent?.getStringExtra(EXTRA_TIMER_STR) ?: "Fermo"

        val notification = buildNotification(cronometroStr, timerStr)
        startForeground(NOTIFICATION_ID, notification)

        return START_NOT_STICKY
    }

    private fun buildNotification(cronometro: String, timer: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resetIntent = Intent(this, WorkoutNotificationService::class.java).apply {
            action = ACTION_RESET_TIMER
        }
        val pendingReset = PendingIntent.getService(
            this, 1, resetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Formato compatto ottimizzato per Notch / Isola Dinamica attorno alla fotocamera
        val titoloCompatto = "⏱️ $cronometro  •  ⏳ $timer"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(titoloCompatto)
            .setContentText("GymTracker • Sessione Attiva")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSubText(cronometro) // Aiuta l'isola dinamica a leggere il tempo primario
            .setContentIntent(pendingOpenApp)
            .addAction(android.R.drawable.ic_menu_rotate, "Reset Timer", pendingReset)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Allenamento In Corso",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifica live per timer e isola dinamica"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
