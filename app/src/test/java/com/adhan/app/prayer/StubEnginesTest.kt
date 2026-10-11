package com.adhan.app.prayer

import com.adhan.app.domain.Alert
import com.adhan.app.domain.AlertType
import com.adhan.app.domain.Coordinates
import com.adhan.app.domain.Schedule
import com.adhan.app.geofence.PlaceRegistration
import org.junit.Assert.assertTrue
import org.junit.Test

class StubEnginesTest {
    @Test
    fun aClockAlarmIsNotAPlaceFence() {
        val armed = PlaceRegistration.armed(
            listOf(
                Alert(
                    id = "clock",
                    type = AlertType.TIME,
                    schedule = Schedule.Time(1L),
                ),
                Alert(
                    id = "masjid",
                    type = AlertType.PLACE,
                    schedule = Schedule.Place(
                        placeId = "masjid",
                        coordinates = Coordinates(21.4225, 39.8262),
                        radiusMeters = 100f,
                    ),
                ),
            ),
        )
        assertTrue(armed.none { it.type == AlertType.TIME })
        assertTrue(armed.any { it.id == "masjid" })
    }
}
