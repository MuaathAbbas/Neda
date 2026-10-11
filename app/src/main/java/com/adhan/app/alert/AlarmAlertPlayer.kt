package com.adhan.app.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.net.toUri
import com.adhan.app.R
import com.adhan.app.domain.Alert
import com.adhan.app.domain.FadeIn

/**
 * Alarm-stream playback. [AudioAttributes.USAGE_ALARM] is intentional:
 * do not switch this to `USAGE_MEDIA` or `STREAM_MUSIC`.
 */
class AlarmAlertPlayer(
    context: Context,
) : AlertPlayer {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    private val handler = Handler(Looper.getMainLooper())
    private val monitor = Any()
    private val listeners = linkedSetOf<AlertPlaybackListener>()
    private var player: MediaPlayer? = null
    private var activeId: String? = null
    private var focusRequest: AudioFocusRequest? = null
    private var stopRunnable: Runnable? = null
    private var fadeRunnable: Runnable? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        Log.i(TAG, "Audio focus change $change; alarm playback continues")
    }

    override fun play(alert: Alert) {
        val previousId = synchronized(monitor) { releaseLocked() }
        if (previousId != null) {
            dispatchFinished(previousId)
        }

        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setAudioAttributes(alarmAttributes())
            mediaPlayer.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK)
            mediaPlayer.setDataSource(appContext, resolveUri(alert))
            mediaPlayer.isLooping = alert.sound.loop
            val startVolume = if (alert.sound.fadeInSeconds > 0) 0f else 1f
            mediaPlayer.setVolume(startVolume, startVolume)
            mediaPlayer.setOnCompletionListener { stop() }
            mediaPlayer.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                stop()
                true
            }
            mediaPlayer.prepare()
            requestFocus()
            synchronized(monitor) {
                player = mediaPlayer
                activeId = alert.id
            }
            mediaPlayer.start()
            if (alert.sound.vibrate) {
                startVibration()
            }
            scheduleFade(alert)
            scheduleStop(alert)
            Log.i(TAG, "Playing alert ${alert.id} with USAGE_ALARM")
        } catch (error: Exception) {
            releasePlayer(mediaPlayer)
            abandonFocus()
            synchronized(monitor) {
                if (player === mediaPlayer) {
                    player = null
                    activeId = null
                }
            }
            throw IllegalStateException("Unable to play alarm audio", error)
        }
    }

    override fun stop() {
        val finishedId = synchronized(monitor) { releaseLocked() }
        if (finishedId != null) {
            dispatchFinished(finishedId)
        }
    }

    override fun isPlaying(): Boolean = synchronized(monitor) {
        val mediaPlayer = player ?: return false
        runCatching { mediaPlayer.isPlaying }.getOrDefault(false)
    }

    override fun currentAlertId(): String? = synchronized(monitor) { activeId }

    override fun isAlarmStreamMuted(): Boolean {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (max == 0) return false
        return audioManager.getStreamVolume(AudioManager.STREAM_ALARM) == 0
    }

    override fun addPlaybackFinishedListener(listener: AlertPlaybackListener) {
        synchronized(monitor) { listeners.add(listener) }
    }

    override fun removePlaybackFinishedListener(listener: AlertPlaybackListener) {
        synchronized(monitor) { listeners.remove(listener) }
    }

    private fun resolveUri(alert: Alert): Uri {
        val custom = alert.sound.soundUri
        if (!custom.isNullOrBlank()) {
            return custom.toUri()
        }
        return "android.resource://${appContext.packageName}/${R.raw.alarm_sample}".toUri()
    }

    private fun alarmAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(alarmAttributes())
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        runCatching { audioManager.abandonAudioFocusRequest(request) }
    }

    private fun scheduleStop(alert: Alert) {
        val seconds = alert.sound.ringSeconds
        if (seconds <= 0) return
        val alertId = alert.id
        val runnable = Runnable {
            if (currentAlertId() == alertId) stop()
        }
        stopRunnable = runnable
        handler.postDelayed(runnable, seconds * 1000L)
    }

    private fun scheduleFade(alert: Alert) {
        val seconds = alert.sound.fadeInSeconds
        if (seconds <= 0) return
        val alertId = alert.id
        val durationMs = seconds * 1000L
        val startedAt = SystemClock.elapsedRealtime()
        val runnable = object : Runnable {
            override fun run() {
                val mediaPlayer = synchronized(monitor) {
                    if (activeId != alertId) return
                    player
                } ?: return
                val volume = FadeIn.volume(SystemClock.elapsedRealtime() - startedAt, durationMs)
                runCatching { mediaPlayer.setVolume(volume, volume) }
                if (volume < 1f && currentAlertId() == alertId) {
                    handler.postDelayed(this, FADE_STEP_MS)
                }
            }
        }
        fadeRunnable = runnable
        handler.post(runnable)
    }

    private fun cancelScheduledStop() {
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null
        fadeRunnable?.let { handler.removeCallbacks(it) }
        fadeRunnable = null
    }

    private fun startVibration() {
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(
            longArrayOf(0, 500, 250, 500, 250, 800),
            0,
        )
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, alarmAttributes())
            }
        }
    }

    private fun releaseLocked(): String? {
        cancelScheduledStop()
        val finishedId = activeId
        activeId = null
        val current = player
        player = null
        releasePlayer(current)
        runCatching { vibrator.cancel() }
        abandonFocus()
        return finishedId
    }

    private fun releasePlayer(mediaPlayer: MediaPlayer?) {
        if (mediaPlayer == null) return
        runCatching { mediaPlayer.setOnCompletionListener(null) }
        runCatching { mediaPlayer.setOnErrorListener(null) }
        runCatching {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
        }
        runCatching { mediaPlayer.release() }
    }

    private fun dispatchFinished(alertId: String) {
        val snapshot = synchronized(monitor) { listeners.toList() }
        snapshot.forEach { listener -> listener.onPlaybackFinished(alertId) }
    }

    private companion object {
        const val TAG = "Adhan"
        const val FADE_STEP_MS = 100L
    }
}
