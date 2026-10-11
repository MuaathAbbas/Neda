package com.adhan.app.alert

import com.adhan.app.domain.Alert

/**
 * Plays one alert at a time on the alarm audio path.
 *
 * Implementations must use alarm usage (`AudioAttributes.USAGE_ALARM` /
 * the alarm stream). They must not use media or notification usage:
 * those follow a connected Bluetooth headset, and the alarm must stay
 * on the phone speaker.
 *
 * [play] replaces any current playback. [stop] ends it.
 */
interface AlertPlayer {
    fun play(alert: Alert)

    fun stop()

    fun isPlaying(): Boolean

    fun currentAlertId(): String?

    /** True when the alarm stream volume is zero. Media volume is irrelevant. */
    fun isAlarmStreamMuted(): Boolean

    fun addPlaybackFinishedListener(listener: AlertPlaybackListener)

    fun removePlaybackFinishedListener(listener: AlertPlaybackListener)
}

fun interface AlertPlaybackListener {
    /** Fired after natural completion, playback failure, or [AlertPlayer.stop]. */
    fun onPlaybackFinished(alertId: String)
}
