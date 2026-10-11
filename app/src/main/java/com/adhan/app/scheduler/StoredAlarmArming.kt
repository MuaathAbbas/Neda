package com.adhan.app.scheduler

import android.content.Context
import android.util.Log
import com.adhan.app.adhanApp
import com.adhan.app.domain.Alert
import com.adhan.app.domain.TimeAlarmRules
import com.adhan.app.prayer.PrayerArming

/** Persists the next clock occurrence. The scheduler interface stays unchanged. */
internal object StoredAlarmArming {
    fun normalizeAll(context: Context) {
        val app = context.adhanApp()
        val now = System.currentTimeMillis()
        app.alertStore.all().forEach { alert ->
            if (alert.id == PomodoroArming.ALERT_ID) return@forEach
            val normalized = TimeAlarmRules.normalizeForArming(alert, now)
            if (normalized != alert) {
                app.alertStore.put(normalized)
            }
        }
    }

    fun advanceAfterFire(context: Context, alert: Alert) {
        if (alert.id == PomodoroArming.ALERT_ID) {
            PomodoroArming.advance(context)
            return
        }
        val now = System.currentTimeMillis()
        val timeUpdated = TimeAlarmRules.afterFire(alert, now)
        val updated = if (timeUpdated != alert) {
            timeUpdated
        } else {
            PrayerArming.afterFire(context, alert, now)
        }
        if (updated == alert) return
        val app = context.adhanApp()
        app.alertStore.put(updated)
        if (!updated.enabled) return
        try {
            app.alertScheduler.schedule(updated)
        } catch (error: Exception) {
            Log.e(TAG, "Could not arm the next occurrence of ${alert.id}", error)
        }
    }

    private const val TAG = "Adhan"
}
