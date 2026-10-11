package com.adhan.app.domain

import com.adhan.app.store.PrayerPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateTextTest {
    @Test
    fun parsesDotCommaAndArabicDigits() {
        assertEquals(21.4225, CoordinateText.parse("21.4225")!!, 0.0000001)
        assertEquals(21.4225, CoordinateText.parse("21,4225")!!, 0.0000001)
        assertEquals(21.4225, CoordinateText.parse("21٫4225")!!, 0.0000001)
        assertEquals(21.4225, CoordinateText.parse("٢١٫٤٢٢٥")!!, 0.0000001)
        assertEquals(-78.6389, CoordinateText.parse("-78.6389")!!, 0.0000001)
        assertNull(CoordinateText.parse("  "))
        assertNull(CoordinateText.parse("north"))
        assertEquals("21.42250", CoordinateText.format(21.4225))
        assertEquals("-78.63890", CoordinateText.format(-78.6389))
        assertEquals(21.4225, CoordinateText.parse(CoordinateText.format(21.4225))!!, 0.0000001)
    }

    @Test
    fun placeUsesTheTypedNumbersAndNothingUntilThen() {
        assertNull(PrayerPlace().coordinates())
        val place = PrayerPlace(latitudeText = "21,4225", longitudeText = "39.8262")
        val coordinates = place.coordinates()!!
        assertEquals(21.4225, coordinates.latitude, 0.0000001)
        assertEquals(39.8262, coordinates.longitude, 0.0000001)
        assertNull(PrayerPlace(latitudeText = "91", longitudeText = "10").coordinates())
    }
}
