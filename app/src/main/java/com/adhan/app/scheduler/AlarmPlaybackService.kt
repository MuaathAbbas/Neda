package com.adhan.app.scheduler

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.adhan.app.adhanApp
import com.adhan.app.alert.AlertPlaybackListener

/**
 * Keeps the process alive while an alarm is audible, then stops.
 *
 * The type stays `shortService`. Official docs allow an exact alarm to start
 * a foreground service from the background, and they describe `mediaPlayback`
 * as continued audio playback with no extra runtime permission. They do not
 * say that a `setAlarmClock` broadcast may use `mediaPlayback`. The type that
 * names `USE_EXACT_ALARM` is `systemExempted`, which is reserved for system
 * integrations. Until that is explicit, playback stops in [onTimeout] instead
 * of switching types.
 */
class AlarmPlaybackService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val notifications by lazy { AlarmNotifications(this) }
    private var ringingId: String? = null
    private var inForeground = false

    private val finishedListener = AlertPlaybackListener { alertId ->
        mainHandler.post {
            if (alertId != ringingId) return@post
            val player = adhanApp().alertPlayer
            if (player.isPlaying() && player.currentAlertId() == ringingId) return@post
            stopAsForeground()
        }
    }

    override fun onCreate() {
        super.onCreate()
        adhanApp().alertPlayer.addPlaybackFinishedListener(finishedListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alert = AlarmPayload.read(this, intent)
        val notificationId = alert?.let { AlarmNotifications.idFor(it.id) }
            ?: AlarmNotifications.FALLBACK_ID
        val notification = if (alert != null) notifications.build(alert) else notifications.fallback()
        startForegroundCompat(notificationId, notification)
        if (alert == null) {
            Log.e(TAG, "Alarm fired without a payload")
            stopAsForeground()
            return START_NOT_STICKY
        }
        ringingId = alert.id
        Log.i(TAG, "Alarm fired id=${alert.id}")
        try {
            adhanApp().alertPlayer.play(alert)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Playback failed", error)
            stopAsForeground()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        stopForTimeout(startId)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopForTimeout(startId)
    }

    private fun stopForTimeout(startId: Int) {
        Log.w(TAG, "shortService timeout startId=$startId")
        runCatching { adhanApp().alertPlayer.stop() }
        stopAsForeground()
    }

    override fun onDestroy() {
        adhanApp().alertPlayer.removePlaybackFinishedListener(finishedListener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(id, notification)
        }
        inForeground = true
    }

    private fun stopAsForeground() {
        if (inForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            inForeground = false
        }
        stopSelf()
    }

    private companion object {
        const val TAG = "Adhan"
    }
}
