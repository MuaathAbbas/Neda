package com.adhan.app.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.adhan.app.adhanApp
import com.adhan.app.alert.AlertPlaybackListener

/** Delivers a due clock alarm to [AlarmPlaybackService] and the alert player. */
class AlarmFiredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_FIRE -> {
                val alert = AlarmPayload.read(context, intent)
                if (alert != null) {
                    StoredAlarmArming.advanceAfterFire(context, alert)
                }
                startPlayback(context, intent)
            }
            ACTION_STOP -> {
                context.adhanApp().alertPlayer.stop()
                context.stopService(Intent(context, AlarmPlaybackService::class.java))
            }
        }
    }

    private fun startPlayback(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            putExtras(intent)
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (error: Exception) {
            Log.e(TAG, "Foreground playback was blocked; playing from the receiver", error)
            playInline(context, intent)
        }
    }

    private fun playInline(context: Context, intent: Intent) {
        val alert = AlarmPayload.read(context, intent) ?: return
        val pending = goAsync()
        val player = context.adhanApp().alertPlayer
        val notifications = AlarmNotifications(context)
        val listener = object : AlertPlaybackListener {
            override fun onPlaybackFinished(alertId: String) {
                if (alertId != alert.id) return
                player.removePlaybackFinishedListener(this)
                notifications.cancel(alert.id)
                pending.finish()
            }
        }
        player.addPlaybackFinishedListener(listener)
        try {
            notifications.notify(alert)
            player.play(alert)
        } catch (error: Exception) {
            Log.e(TAG, "Inline playback failed", error)
            player.removePlaybackFinishedListener(listener)
            notifications.cancel(alert.id)
            pending.finish()
        }
    }

    companion object {
        const val ACTION_FIRE = "com.adhan.app.action.ALARM_FIRE"
        const val ACTION_STOP = "com.adhan.app.action.ALARM_STOP"
        private const val TAG = "Adhan"
    }
}
