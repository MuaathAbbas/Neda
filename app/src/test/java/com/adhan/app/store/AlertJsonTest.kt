package com.adhan.app.store

import com.adhan.app.domain.Alert
import com.adhan.app.domain.AlertType
import com.adhan.app.domain.Coordinates
import com.adhan.app.domain.Prayer
import com.adhan.app.domain.Schedule
import com.adhan.app.domain.SoundSettings
import com.adhan.app.domain.TimeRepeat
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AlertJsonTest {
    @Test
    fun roundTripsEveryScheduleKind() {
        val alerts = listOf(
            Alert(
                id = "time-1",
                type = AlertType.TIME,
                schedule = Schedule.Time(1_700_000_000_000L),
                sound = SoundSettings(
                    soundUri = "content://adhan/sample",
                    vibrate = false,
                    loop = true,
                    ringSeconds = 30,
                ),
                bypassDnd = false,
                enabled = false,
                label = "Clock",
            ),
            Alert(
                id = "daily-1",
                type = AlertType.TIME,
                schedule = Schedule.Time(
                    triggerAtEpochMillis = 1_700_000_000_000L,
                    repeat = TimeRepeat.DAILY,
                    hour = 5,
                    minute = 30,
                ),
                label = "Fajr reminder",
            ),
            Alert(
                id = "prayer-1",
                type = AlertType.PRAYER,
                schedule = Schedule.Prayer(Prayer.MAGHRIB, 1_800_000_000_000L),
                label = "Maghrib",
            ),
            Alert(
                id = "place-1",
                type = AlertType.PLACE,
                schedule = Schedule.Place(
                    placeId = "masjid",
                    coordinates = Coordinates(21.4225, 39.8262),
                    radiusMeters = 150f,
                ),
            ),
        )
        alerts.forEach { original ->
            val decoded = AlertJson.decode(JSONObject(AlertJson.encode(original).toString()))
            assertEquals(original, decoded)
        }
    }

    @Test
    fun legacyTimeJsonDecodesAsOnce() {
        val json = JSONObject()
            .put("id", "old")
            .put("type", "TIME")
            .put("scheduleKind", "TIME")
            .put("triggerAtEpochMillis", 10L)
        val time = AlertJson.decode(json).schedule as Schedule.Time
        assertEquals(TimeRepeat.ONCE, time.repeat)
        assertEquals(null, time.hour)
        assertEquals(null, time.minute)
    }

    @Test
    fun decodeRejectsMismatchedType() {
        val json = JSONObject()
            .put("id", "x")
            .put("type", "TIME")
            .put("scheduleKind", "PLACE")
            .put("placeId", "masjid")
            .put("latitude", 21.4)
            .put("longitude", 39.8)
            .put("radiusMeters", 100.0)
        assertThrows(IllegalArgumentException::class.java) {
            AlertJson.decode(json)
        }
    }
}
