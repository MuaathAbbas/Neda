package com.adhan.app.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.adhan.app.calendar.ReminderNotifier
import com.adhan.app.prayer.PrayerArming

/** Recalculates stored prayer times when the day or the clock changes. */
class PrayerDayRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == ACTION_REMIND) {
            ReminderNotifier.showTomorrow(context)
            ReminderAlarm.schedule(context)
            return
        }
        if (action != ACTION_REFRESH &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_TIME_CHANGED
        ) {
            return
        }
        try {
            PrayerArming.refreshStored(context)
            PrayerArming.armStored(context)
        } catch (error: Exception) {
            Log.e(TAG, "Could not refresh prayer times", error)
            PrayerDayRefresh.schedule(context)
        }
        ReminderAlarm.schedule(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.adhan.app.action.PRAYER_DAY_REFRESH"
        const val ACTION_REMIND = "com.adhan.app.action.DAY_REMIND"
        private const val TAG = "Adhan"
    }
}
