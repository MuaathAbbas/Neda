package com.adhan.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.adhan.app.prayer.PrayerDayUpdate
import java.time.ZoneId

/**
 * Silent exact alarm that recomputes prayer times after local midnight.
 * It does not ring. The next night is armed again after each refresh.
 */
internal object PrayerDayRefresh {
    fun schedule(
        context: Context,
        nowEpochMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        val triggerAt = PrayerDayUpdate.nextMillis(nowEpochMillis, zone)
        val operation = pending(app)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
            Log.i(TAG, "Prayer times will refresh at $triggerAt")
        } catch (security: SecurityException) {
            Log.w(TAG, "Exact prayer refresh was blocked; using an inexact alarm", security)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        val operation = pending(app)
        alarmManager.cancel(operation)
        operation.cancel()
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, PrayerDayRefreshReceiver::class.java).apply {
            action = PrayerDayRefreshReceiver.ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            context,
            AlarmRequestCodes.prayerDay(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val TAG = "Adhan"
}
