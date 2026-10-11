package com.adhan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextAlarmTest {
    @Test
    fun picksTheSoonestEnabledFutureAlarm() {
        val later = clock("later", triggerAt = 300)
        val sooner = clock("sooner", triggerAt = 200)
        val past = clock("past", triggerAt = 50)
        val off = clock("off", triggerAt = 150, enabled = false)
        val place = Alert(
            id = "place",
            type = AlertType.PLACE,
            schedule = Schedule.Place("masjid", Coordinates(21.4, 39.8), 100f),
        )
        val chosen = NextAlarm.soonest(listOf(later, past, off, place, sooner), nowEpochMillis = 100)
        assertEquals("sooner", chosen?.id)
    }

    @Test
    fun returnsNullWhenNothingIsStillAhead() {
        assertNull(NextAlarm.soonest(emptyList(), nowEpochMillis = 0))
        assertNull(NextAlarm.soonest(listOf(clock("past", triggerAt = 10)), nowEpochMillis = 10))
    }

    private fun clock(id: String, triggerAt: Long, enabled: Boolean = true): Alert {
        return Alert(
            id = id,
            type = AlertType.TIME,
            schedule = Schedule.Time(triggerAt),
            enabled = enabled,
        )
    }
}
