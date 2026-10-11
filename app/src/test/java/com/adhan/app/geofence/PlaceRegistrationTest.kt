package com.adhan.app.geofence

import com.adhan.app.domain.Alert
import com.adhan.app.domain.AlertType
import com.adhan.app.domain.Coordinates
import com.adhan.app.domain.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceRegistrationTest {
    @Test
    fun onlyEnabledPlacesAreArmed() {
        val place = place("masjid", enabled = true)
        val off = place("home", enabled = false)
        val clock = Alert(
            id = "clock",
            type = AlertType.TIME,
            schedule = Schedule.Time(1L),
        )
        val armed = PlaceRegistration.armed(listOf(place, off, clock))
        assertEquals(listOf(place.id), armed.map { it.id })
    }

    @Test
    fun enterIsQuietForFifteenMinutes() {
        val now = 1_000_000L
        assertTrue(PlaceEnterPolicy.allow(null, now))
        assertFalse(PlaceEnterPolicy.allow(now - 1_000L, now))
        assertTrue(PlaceEnterPolicy.allow(now - PlaceEnterPolicy.WINDOW_MS, now))
    }

    @Test
    fun anOpenInsideTheCircleDoesNotCountAsEntering() {
        assertTrue(PlaceEnterPolicy.inside(0f, 200f))
        assertTrue(PlaceEnterPolicy.inside(200f, 200f))
        assertFalse(PlaceEnterPolicy.inside(201f, 200f))
        assertFalse(PlaceEnterPolicy.inside(null, 200f))
    }

    private fun place(id: String, enabled: Boolean): Alert {
        return Alert(
            id = id,
            type = AlertType.PLACE,
            schedule = Schedule.Place(
                placeId = id,
                coordinates = Coordinates(21.4225, 39.8262),
                radiusMeters = 200f,
            ),
            enabled = enabled,
            label = id,
        )
    }
}
