package com.adhan.app.store

import android.content.Context
import com.adhan.app.domain.AlertType
import com.adhan.app.domain.SoundSettings

/** Which alerts share one chosen sound. */
enum class SoundRole {
    PRAYER,
    CLOCK,
    PLACE,
}

/**
 * Remembers a ringtone for each [SoundRole]. A blank value means the bundled tone.
 */
class PreferencesSoundChoiceStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(role: SoundRole): String? {
        return prefs.getString(role.name, null)?.takeIf { it.isNotBlank() }
    }

    fun write(role: SoundRole, uri: String?) {
        val editor = prefs.edit()
        if (uri.isNullOrBlank()) {
            editor.remove(role.name)
        } else {
            editor.putString(role.name, uri)
        }
        editor.commit()
    }

    fun vibrateEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATE, true)

    fun setVibrate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATE, enabled).apply()
    }

    fun ringSeconds(): Int = prefs.getInt(KEY_RING_SECONDS, 0).coerceIn(0, 600)

    fun setRingSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_RING_SECONDS, seconds.coerceIn(0, 600)).apply()
    }

    fun fadeInSeconds(): Int = allowedFade(prefs.getInt(KEY_FADE_IN_SECONDS, 0))

    fun setFadeInSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_FADE_IN_SECONDS, allowedFade(seconds)).apply()
    }

    fun settings(role: SoundRole, loop: Boolean = true): SoundSettings {
        return SoundSettings(
            soundUri = read(role),
            vibrate = vibrateEnabled(),
            loop = loop,
            ringSeconds = ringSeconds(),
            fadeInSeconds = fadeInSeconds(),
        )
    }

    /** Saved alarms keep ringing until Stop. Older rows were stored without looping. */
    fun ensureLooping(alerts: AlertStore) {
        alerts.all().filter { !it.sound.loop }.forEach { alert ->
            alerts.put(alert.copy(sound = alert.sound.copy(loop = true)))
        }
    }

    /** Copies the chosen sound onto alerts of that role already in the store. */
    fun applyTo(role: SoundRole, alerts: AlertStore) {
        val settings = settings(role)
        val type = when (role) {
            SoundRole.PRAYER -> AlertType.PRAYER
            SoundRole.CLOCK -> AlertType.TIME
            SoundRole.PLACE -> AlertType.PLACE
        }
        alerts.all().filter { it.type == type && it.sound != settings }.forEach { alert ->
            alerts.put(alert.copy(sound = settings))
        }
    }

    private companion object {
        const val PREFS = "adhan_sounds"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_RING_SECONDS = "ring_seconds"
        const val KEY_FADE_IN_SECONDS = "fade_in_seconds"

        private fun allowedFade(seconds: Int): Int = if (seconds == 4 || seconds == 10) seconds else 0
    }
}
