package com.adhan.app.prayer

import java.time.Instant
import java.time.ZoneId

/** When the stored prayer day should roll forward. */
object PrayerDayUpdate {
    const val DELAY_MS = 15_000L

    /**
     * A few seconds after the next local midnight, so the civil date has
     * already changed when the times are recalculated.
     */
    fun nextMillis(nowEpochMillis: Long, zone: ZoneId): Long {
        val nextMidnight = Instant.ofEpochMilli(nowEpochMillis)
            .atZone(zone)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        return nextMidnight + DELAY_MS
    }
}
