package com.adhan.app.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.adhan.app.adhanApp
import com.adhan.app.alert.AlertPlaybackListener
import com.adhan.app.domain.AlertType
import com.adhan.app.scheduler.AlarmNotifications
import com.adhan.app.scheduler.AlarmPayload
import com.adhan.app.scheduler.AlarmPlaybackService

/** Plays the alarm sound when the device enters a saved place. Exit is ignored. */
class PlaceEnteredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_ENTER) return
        if (!intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)) return
        val alert = AlarmPayload.read(context, intent) ?: return
        if (!alert.enabled || alert.type != AlertType.PLACE) return
        val prefs = context.getSharedPreferences(PlaceEnterPolicy.PREFS, Context.MODE_PRIVATE)
        val last = if (prefs.contains(alert.id)) prefs.getLong(alert.id, 0L) else null
        val now = System.currentTimeMillis()
        if (!PlaceEnterPolicy.allow(last, now)) {
            Log.i(TAG, "Skipped repeat enter for ${alert.id}")
            return
        }
        prefs.edit().putLong(alert.id, now).commit()
        startPlayback(context, intent)
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
        const val ACTION_ENTER = "com.adhan.app.action.PLACE_ENTER"
        private const val TAG = "Adhan"
    }
}
