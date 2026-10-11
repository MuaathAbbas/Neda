package com.adhan.app.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Next wall-clock instant for a user-chosen hour and minute.
 * This is ordinary clock arithmetic, not prayer calculation.
 */
object TimeTriggers {
    fun nextOccurrence(
        hour: Int,
        minute: Int,
        afterEpochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        require(hour in 0..23) { "Hour out of range: $hour" }
        require(minute in 0..59) { "Minute out of range: $minute" }
        val after = Instant.ofEpochMilli(afterEpochMillis).atZone(zone)
        var candidate = ZonedDateTime.of(after.toLocalDate(), LocalTime.of(hour, minute), zone)
        if (candidate.toInstant().toEpochMilli() <= afterEpochMillis) {
            candidate = candidate.plusDays(1)
        }
        return candidate.toInstant().toEpochMilli()
    }
}

/**
 * What a stored clock alarm should look like after it fires, and when a past
 * trigger must be moved forward before it is armed again.
 */
object TimeAlarmRules {
    fun afterFire(
        alert: Alert,
        firedAtEpochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Alert {
        if (!alert.enabled) return alert
        val time = alert.schedule as? Schedule.Time ?: return alert
        return when (time.repeat) {
            TimeRepeat.ONCE -> alert.copy(enabled = false)
            TimeRepeat.DAILY -> advanceDaily(alert, time, firedAtEpochMillis, zone)
        }
    }

    /**
     * Keeps an enabled alarm point at a future instant.
     * A missed once-alarm is turned off. A missed daily alarm moves to the
     * next local occurrence.
     */
    fun normalizeForArming(
        alert: Alert,
        nowEpochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Alert {
        if (!alert.enabled) return alert
        val time = alert.schedule as? Schedule.Time ?: return alert
        if (time.triggerAtEpochMillis > nowEpochMillis) return alert
        return when (time.repeat) {
            TimeRepeat.ONCE -> alert.copy(enabled = false)
            TimeRepeat.DAILY -> advanceDaily(alert, time, nowEpochMillis, zone)
        }
    }

    private fun advanceDaily(
        alert: Alert,
        time: Schedule.Time,
        afterEpochMillis: Long,
        zone: ZoneId,
    ): Alert {
        val hour = time.hour
        val minute = time.minute
        if (hour == null || minute == null) return alert.copy(enabled = false)
        val next = TimeTriggers.nextOccurrence(hour, minute, afterEpochMillis, zone)
        return alert.copy(schedule = time.copy(triggerAtEpochMillis = next))
    }
}
