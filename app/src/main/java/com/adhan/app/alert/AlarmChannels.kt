package com.adhan.app.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import com.adhan.app.R

/**
 * High-importance alarm channels. The audible channel is created with
 * [NotificationChannel.setBypassDnd] set, which Android honors only after
 * the user grants notification-policy access.
 *
 * Channel sound is empty on purpose. [AlarmAlertPlayer] owns playback so
 * the stream stays `USAGE_ALARM`. Notifications posted for these channels
 * use category alarm.
 */
object AlarmChannels {
    const val ALARM_CHANNEL_ID = "adhan_alarms"
    const val RESPECT_DND_CHANNEL_ID = "adhan_alarms_respect_dnd"
    const val REMINDER_CHANNEL_ID = "adhan_reminders"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(build(context, bypassDnd = true))
        manager.createNotificationChannel(build(context, bypassDnd = false))
        manager.createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL_ID,
                context.getString(R.string.channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_reminders_description)
                setSound(null, null as AudioAttributes?)
                enableVibration(false)
            },
        )
    }

    fun channelIdFor(bypassDnd: Boolean): String {
        return if (bypassDnd) ALARM_CHANNEL_ID else RESPECT_DND_CHANNEL_ID
    }

    private fun build(context: Context, bypassDnd: Boolean): NotificationChannel {
        val id = channelIdFor(bypassDnd)
        val nameRes = if (bypassDnd) R.string.channel_name else R.string.channel_respect_name
        val descriptionRes =
            if (bypassDnd) R.string.channel_description else R.string.channel_respect_description
        return NotificationChannel(id, context.getString(nameRes), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(descriptionRes)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(bypassDnd)
            enableVibration(false)
            setSound(null, null as AudioAttributes?)
            setShowBadge(true)
        }
    }
}
