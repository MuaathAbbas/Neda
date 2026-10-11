package com.adhan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeAlarmRulesTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun nextOccurrenceUsesLaterToday() {
        val after = utc(2026, 9, 26, 10, 15)
        val next = TimeTriggers.nextOccurrence(11, 0, after, zone)
        assertEquals(utc(2026, 9, 26, 11, 0), next)
    }

    @Test
    fun nextOccurrenceRollsToTomorrowWhenTimeHasPassed() {
        val after = utc(2026, 9, 26, 10, 15)
        val next = TimeTriggers.nextOccurrence(9, 0, after, zone)
        assertEquals(utc(2026, 9, 27, 9, 0), next)
    }

    @Test
    fun dailyAlarmAdvancesAfterItFires() {
        val firedAt = utc(2026, 9, 26, 5, 30)
        val alert = clock("daily", firedAt, TimeRepeat.DAILY, 5, 30)
        val updated = TimeAlarmRules.afterFire(alert, firedAt, zone)
        assertTrue(updated.enabled)
        assertEquals(utc(2026, 9, 27, 5, 30), updated.triggerAtEpochMillis)
    }

    @Test
    fun onceAlarmTurnsOffAfterItFires() {
        val alert = clock("once", utc(2026, 9, 26, 5, 30), TimeRepeat.ONCE, 5, 30)
        val updated = TimeAlarmRules.afterFire(alert, alert.triggerAtEpochMillis!!, zone)
        assertFalse(updated.enabled)
    }

    @Test
    fun missedDailyAlarmMovesForwardWhenArming() {
        val now = utc(2026, 9, 26, 12, 0)
        val missed = clock("daily", utc(2026, 9, 26, 5, 30), TimeRepeat.DAILY, 5, 30)
        val updated = TimeAlarmRules.normalizeForArming(missed, now, zone)
        assertEquals(utc(2026, 9, 27, 5, 30), updated.triggerAtEpochMillis)
        assertTrue(updated.enabled)
    }

    @Test
    fun missedOnceAlarmTurnsOffWhenArming() {
        val now = utc(2026, 9, 26, 12, 0)
        val missed = clock("once", utc(2026, 9, 26, 5, 30), TimeRepeat.ONCE, 5, 30)
        val updated = TimeAlarmRules.normalizeForArming(missed, now, zone)
        assertFalse(updated.enabled)
    }

    @Test
    fun futureAlarmIsLeftAlone() {
        val now = utc(2026, 9, 26, 4, 0)
        val upcoming = clock("daily", utc(2026, 9, 26, 5, 30), TimeRepeat.DAILY, 5, 30)
        assertEquals(upcoming, TimeAlarmRules.normalizeForArming(upcoming, now, zone))
    }

    @Test
    fun dailyScheduleRequiresAWallClock() {
        assertThrows(IllegalArgumentException::class.java) {
            Schedule.Time(
                triggerAtEpochMillis = 1L,
                repeat = TimeRepeat.DAILY,
            )
        }
    }

    private fun clock(
        id: String,
        triggerAt: Long,
        repeat: TimeRepeat,
        hour: Int,
        minute: Int,
    ): Alert {
        return Alert(
            id = id,
            type = AlertType.TIME,
            schedule = Schedule.Time(triggerAt, repeat, hour, minute),
            label = id,
        )
    }

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
    }
}
