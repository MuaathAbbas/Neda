package com.adhan.app.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class PrayerDayTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")

    @Test
    fun updatesJustAfterTheNextLocalMidnight() {
        val evening = ZonedDateTime.of(LocalDate.of(2026, 10, 10), LocalTime.of(21, 30), riyadh)
        val expected = ZonedDateTime.of(LocalDate.of(2026, 10, 11), LocalTime.MIDNIGHT, riyadh)
            .toInstant()
            .toEpochMilli() + PrayerDayUpdate.DELAY_MS
        assertEquals(expected, PrayerDayUpdate.nextMillis(evening.toInstant().toEpochMilli(), riyadh))
    }

    @Test
    fun aMomentAfterMidnightWaitsForTheFollowingNight() {
        val justAfter = ZonedDateTime.of(LocalDate.of(2026, 10, 11), LocalTime.of(0, 0, 20), riyadh)
        val next = PrayerDayUpdate.nextMillis(justAfter.toInstant().toEpochMilli(), riyadh)
        val followingMidnight = ZonedDateTime.of(LocalDate.of(2026, 10, 12), LocalTime.MIDNIGHT, riyadh)
            .toInstant()
            .toEpochMilli()
        assertTrue(next > justAfter.toInstant().toEpochMilli())
        assertEquals(followingMidnight + PrayerDayUpdate.DELAY_MS, next)
    }
}
