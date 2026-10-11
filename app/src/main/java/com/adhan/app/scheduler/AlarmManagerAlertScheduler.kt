package com.adhan.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.adhan.app.AdhanApp
import com.adhan.app.alert.AlarmLaunch
import com.adhan.app.domain.Alert

/**
 * User-visible clock alarms via [AlarmManager.setAlarmClock].
 * That API is the one the platform treats like a manufacturer alarm:
 * it is exact, it shows the alarm icon, and it is allowed to wake the device.
 */
class AlarmManagerAlertScheduler(
    context: Context,
) : AlertScheduler {
    private val context = context.applicationContext
    private val alarmManager = this.context.getSystemService(AlarmManager::class.java)

    override fun schedule(alert: Alert) {
        if (!alert.enabled) {
            throw IllegalArgumentException("Refusing to schedule disabled alert ${alert.id}.")
        }
        val triggerAt = alert.triggerAtEpochMillis
            ?: throw IllegalArgumentException(
                "Only TIME and PRAYER alerts are scheduled with AlertScheduler. " +
                    "PLACE alerts belong to GeofenceEngine.",
            )
        if (!canScheduleExactAlarms()) {
            throw ExactAlarmNotPermittedException()
        }
        val operation = PendingIntent.getBroadcast(
            context,
            AlarmRequestCodes.fire(alert.id),
            fireIntent(alert),
            PENDING_FLAGS,
        )
        val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent(alert))
        try {
            alarmManager.setAlarmClock(info, operation)
        } catch (security: SecurityException) {
            throw ExactAlarmNotPermittedException()
        }
        Log.i(TAG, "setAlarmClock id=${alert.id} at=$triggerAt")
    }

    override fun cancel(alertId: String) {
        val operation = PendingIntent.getBroadcast(
            context,
            AlarmRequestCodes.fire(alertId),
            Intent(context, AlarmFiredReceiver::class.java).setAction(AlarmFiredReceiver.ACTION_FIRE),
            PENDING_FLAGS,
        )
        alarmManager.cancel(operation)
        operation.cancel()
        Log.i(TAG, "cancelled id=$alertId")
    }

    override fun rescheduleAll(alerts: List<Alert>) {
        val now = System.currentTimeMillis()
        alerts.forEach { alert ->
            val triggerAt = alert.triggerAtEpochMillis
            if (!alert.enabled || triggerAt == null) return@forEach
            if (triggerAt <= now) {
                Log.i(TAG, "skip past alert ${alert.id}")
                return@forEach
            }
            schedule(alert)
        }
    }

    override fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun fireIntent(alert: Alert): Intent {
        return Intent(context, AlarmFiredReceiver::class.java).apply {
            action = AlarmFiredReceiver.ACTION_FIRE
            AlarmPayload.write(this, alert)
        }
    }

    private fun showIntent(alert: Alert): PendingIntent {
        val screen = (context as AdhanApp).ringingActivity
        val intent = Intent(context, screen).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmLaunch.EXTRA_RINGING, true)
            putExtra(AlarmPayload.EXTRA_ALERT_ID, alert.id)
        }
        return PendingIntent.getActivity(
            context,
            AlarmRequestCodes.show(alert.id),
            intent,
            PENDING_FLAGS,
        )
    }

    private companion object {
        const val TAG = "Adhan"
        const val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
