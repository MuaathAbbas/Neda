package com.adhan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AlertTest {
    @Test
    fun timeAlertExposesItsTrigger() {
        val alert = Alert(
            id = "a",
            type = AlertType.TIME,
            schedule = Schedule.Time(1_700_000_000_000L),
        )
        assertEquals(1_700_000_000_000L, alert.triggerAtEpochMillis)
    }

    @Test
    fun prayerAlertExposesItsTrigger() {
        val alert = Alert(
            id = "fajr",
            type = AlertType.PRAYER,
            schedule = Schedule.Prayer(Prayer.FAJR, 1_800_000_000_000L),
        )
        assertEquals(1_800_000_000_000L, alert.triggerAtEpochMillis)
    }

    @Test
    fun placeAlertHasNoClockTrigger() {
        val alert = Alert(
            id = "masjid",
            type = AlertType.PLACE,
            schedule = Schedule.Place(
                placeId = "masjid",
                coordinates = Coordinates(21.4225, 39.8262),
                radiusMeters = 80f,
            ),
        )
        assertNull(alert.triggerAtEpochMillis)
    }

    @Test
    fun typeMustMatchSchedule() {
        assertThrows(IllegalArgumentException::class.java) {
            Alert(
                id = "mismatch",
                type = AlertType.PLACE,
                schedule = Schedule.Time(1L),
            )
        }
    }
}
