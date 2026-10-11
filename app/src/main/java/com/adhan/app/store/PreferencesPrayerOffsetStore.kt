package com.adhan.app.store

import android.content.Context

/** Remembers the reminder and iqamah durations chosen in settings. */
class PreferencesPrayerOffsetStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): PrayerOffsets {
        return PrayerOffsets(
            beforeMinutes = prefs.getInt(KEY_BEFORE, 0).coerceIn(0, 180),
            iqamahMinutes = prefs.getInt(KEY_IQAMAH, 0).coerceIn(0, 180),
        )
    }

    fun write(offsets: PrayerOffsets) {
        prefs.edit()
            .putInt(KEY_BEFORE, offsets.beforeMinutes.coerceIn(0, 180))
            .putInt(KEY_IQAMAH, offsets.iqamahMinutes.coerceIn(0, 180))
            .apply()
    }

    private companion object {
        const val PREFS = "adhan_prayer_offsets"
        const val KEY_BEFORE = "before"
        const val KEY_IQAMAH = "iqamah"
    }
}
