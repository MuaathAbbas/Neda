package com.adhan.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.adhan.app.scheduler.AlarmRequestCodes

/** Refreshes the home widget at the next minute so the prayer countdown moves. */
internal object WidgetTicker {
    fun schedule(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        val triggerAt = ((System.currentTimeMillis() / 60_000L) + 1L) * 60_000L
        val operation = pending(app)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val operation = pending(app)
        app.getSystemService(AlarmManager::class.java).cancel(operation)
        operation.cancel()
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, HomeWidgetProvider::class.java).apply {
            action = HomeWidgetProvider.ACTION_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            AlarmRequestCodes.widgetTick(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
