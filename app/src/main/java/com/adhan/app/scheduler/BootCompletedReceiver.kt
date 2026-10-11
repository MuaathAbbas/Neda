package com.adhan.app.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.adhan.app.adhanApp
import com.adhan.app.alert.AlarmChannels
import com.adhan.app.prayer.PrayerArming
import com.adhan.app.widget.HomeWidgetRenderer

/**
 * Moves missed daily alarms forward, turns off missed once-alarms,
 * recomputes prayer alerts from the saved coordinates, then re-arms
 * every enabled future alert, then registers saved places again.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!shouldReschedule(intent?.action)) return
        AlarmChannels.ensureCreated(context)
        val app = context.adhanApp()
        StoredAlarmArming.normalizeAll(context)
        try {
            PrayerArming.refreshStored(context)
        } catch (error: Exception) {
            Log.e(TAG, "Could not recompute prayer times after boot", error)
        }
        PomodoroArming.rearm(context)
        app.alertScheduler.rescheduleAll(app.alertStore.all())
        ReminderAlarm.schedule(context)
        HomeWidgetRenderer.refreshAll(context)
        try {
            app.geofenceEngine.refresh(app.alertStore.all())
        } catch (error: Exception) {
            Log.e(TAG, "Could not register places after boot", error)
        }
        Log.i(TAG, "Rescheduled stored alarms after boot")
    }

    companion object {
        private const val TAG = "Adhan"
        private const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
        private const val ACTION_HTC_QUICKBOOT = "com.htc.intent.action.QUICKBOOT_POWERON"

        /** Boot, Huawei/HTC quick boot, and app update. Alarms are cleared on update. */
        internal fun shouldReschedule(action: String?): Boolean {
            return action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == ACTION_QUICKBOOT ||
                action == ACTION_HTC_QUICKBOOT
        }
    }
}
