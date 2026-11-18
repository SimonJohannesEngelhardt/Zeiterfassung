package de.zeiterfassung.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.zeiterfassung.data.SQLiteWorkIntervalStore
import de.zeiterfassung.domain.TimeTracking

class TimerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (TimeTracking(SQLiteWorkIntervalStore(context)).activeInterval() != null) {
            context.startForegroundService(
                Intent(context, TimerNotificationService::class.java)
            )
        }
    }
}
