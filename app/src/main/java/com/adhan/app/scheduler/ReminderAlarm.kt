package com.adhan.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.adhan.app.domain.TimeTriggers
import java.time.ZoneId

/** Evening check for tomorrow's occasion. It does not play a sound. */
internal object ReminderAlarm {
    fun schedule(context: Context, nowEpochMillis: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        val triggerAt = TimeTriggers.nextOccurrence(19, 0, nowEpochMillis, ZoneId.systemDefault())
        val operation = pending(app)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        } catch (security: SecurityException) {
            Log.w(TAG, "Exact reminder was blocked", security)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, PrayerDayRefreshReceiver::class.java).apply {
            action = PrayerDayRefreshReceiver.ACTION_REMIND
        }
        return PendingIntent.getBroadcast(
            context,
            AlarmRequestCodes.reminder(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val TAG = "Adhan"
}
