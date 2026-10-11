package com.adhan.app.scheduler

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.adhan.app.AdhanApp
import com.adhan.app.R
import com.adhan.app.alert.AlarmChannels
import com.adhan.app.alert.AlarmLaunch
import com.adhan.app.domain.Alert
import com.adhan.app.domain.Prayer
import com.adhan.app.prayer.PrayerAlerts

/** Alarm-category notification shown while a clock alert is ringing. */
internal class AlarmNotifications(
    private val context: Context,
) {
    fun build(alert: Alert): android.app.Notification {
        AlarmChannels.ensureCreated(context)
        val title = alertTitle(alert)
        val open = contentIntent(alert)
        return base(AlarmChannels.channelIdFor(alert.bypassDnd))
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_ringing))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(
                R.drawable.ic_stat_alarm,
                context.getString(R.string.notification_stop),
                stopIntent(alert),
            )
            .build()
    }

    fun fallback(): android.app.Notification {
        AlarmChannels.ensureCreated(context)
        return base(AlarmChannels.ALARM_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.status_schedule_failed))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun notify(alert: Alert) {
        try {
            NotificationManagerCompat.from(context).notify(idFor(alert.id), build(alert))
        } catch (security: SecurityException) {
            Log.w(TAG, "Notification was blocked", security)
        }
    }

    fun cancel(alertId: String) {
        NotificationManagerCompat.from(context).cancel(idFor(alertId))
    }

    private fun alertTitle(alert: Alert): String {
        val parsed = PrayerAlerts.parse(alert.id)
        if (parsed != null) {
            val name = context.getString(prayerName(parsed.first))
            return when (parsed.second) {
                PrayerAlerts.Role.ADHAN -> name
                PrayerAlerts.Role.BEFORE -> context.getString(R.string.alarm_before, name)
                PrayerAlerts.Role.IQAMAH -> context.getString(R.string.alarm_iqamah, name)
            }
        }
        return alert.label.ifBlank { context.getString(R.string.app_name) }
    }

    private fun base(channelId: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    }

    private fun contentIntent(alert: Alert): PendingIntent {
        val screen = (context.applicationContext as AdhanApp).ringingActivity
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

    private fun stopIntent(alert: Alert): PendingIntent {
        val intent = Intent(context, AlarmFiredReceiver::class.java).apply {
            action = AlarmFiredReceiver.ACTION_STOP
            putExtra(AlarmPayload.EXTRA_ALERT_ID, alert.id)
        }
        return PendingIntent.getBroadcast(
            context,
            AlarmRequestCodes.stop(alert.id),
            intent,
            PENDING_FLAGS,
        )
    }

    companion object {
        const val FALLBACK_ID = 1001

        fun idFor(alertId: String): Int = AlarmRequestCodes.notification(alertId)

        private const val TAG = "Adhan"

        private fun prayerName(prayer: Prayer): Int {
            return when (prayer) {
                Prayer.FAJR -> R.string.prayer_fajr
                Prayer.SUNRISE -> R.string.prayer_sunrise
                Prayer.DHUHR -> R.string.prayer_dhuhr
                Prayer.ASR -> R.string.prayer_asr
                Prayer.MAGHRIB -> R.string.prayer_maghrib
                Prayer.ISHA -> R.string.prayer_isha
            }
        }
        private const val PENDING_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
