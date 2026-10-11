package com.adhan.app.store

import android.content.Context

/** Wall-clock time of the last prayer refresh that stored its alerts. */
class PrayerRefreshLog(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun write(epochMillis: Long) {
        prefs.edit().putLong(KEY_AT, epochMillis).apply()
    }

    fun read(): Long? = if (prefs.contains(KEY_AT)) prefs.getLong(KEY_AT, 0L) else null

    fun clear() {
        prefs.edit().remove(KEY_AT).apply()
    }

    private companion object {
        const val PREFS = "adhan_reliability"
        const val KEY_AT = "prayer_refresh_at"
    }
}
