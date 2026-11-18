package de.zeiterfassung.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import de.zeiterfassung.MainActivity
import de.zeiterfassung.R
import de.zeiterfassung.data.SQLiteWorkIntervalStore
import de.zeiterfassung.domain.TimeTracking

class TimerNotificationService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tracking by lazy { TimeTracking(SQLiteWorkIntervalStore(this)) }
    private val refreshNotification = object : Runnable {
        override fun run() {
            val active = tracking.activeInterval()
            if (active == null) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            updateNotification(active.startEpochMillis)
            handler.postDelayed(this, NOTIFICATION_UPDATE_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            tracking.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val active = tracking.activeInterval()
        if (active == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground(active.startEpochMillis)
        handler.removeCallbacks(refreshNotification)
        handler.postDelayed(refreshNotification, NOTIFICATION_UPDATE_MILLIS)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(refreshNotification)
        super.onDestroy()
    }

    private fun startAsForeground(startEpochMillis: Long) {
        val notification = buildNotification(startEpochMillis)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(startEpochMillis: Long) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(startEpochMillis))
    }

    private fun buildNotification(startEpochMillis: Long): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopTimer = PendingIntent.getService(
            this,
            1,
            Intent(this, TimerNotificationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val elapsedMillis = System.currentTimeMillis() - startEpochMillis
        val elapsedText = formatDuration(elapsedMillis)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(getString(R.string.timer_notification_title))
            .setContentText(getString(R.string.timer_notification_duration, elapsedText))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.stop),
                    stopTimer
                ).build()
            )
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.timer_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun formatDuration(elapsedMillis: Long): String {
        val minutes = (elapsedMillis + 30_000L).coerceAtLeast(0L) / 60_000L
        return "%d:%02d".format(minutes / 60, minutes % 60)
    }

    companion object {
        const val ACTION_STOP = "de.zeiterfassung.action.STOP_TIMER"
        private const val CHANNEL_ID = "work_timer"
        private const val NOTIFICATION_ID = 10
        private const val NOTIFICATION_UPDATE_MILLIS = 60_000L
    }
}
