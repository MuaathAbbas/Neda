package com.adhan.app.prayer

import com.adhan.app.domain.Coordinates
import kotlin.math.abs

/**
 * Named places that only copy coordinates into the fields the user can edit.
 * Calculation never reads the city id. It reads the coordinates it is given.
 */
data class PrayerCity(
    val id: String,
    val latitude: Double,
    val longitude: Double,
) {
    fun coordinates(): Coordinates = Coordinates(latitude, longitude)
}

object PrayerCities {
    val all: List<PrayerCity> = listOf(
        PrayerCity("makkah", 21.4225, 39.8262),
        PrayerCity("madinah", 24.4672, 39.6111),
        PrayerCity("riyadh", 24.7136, 46.6753),
        PrayerCity("jeddah", 21.5433, 39.1728),
        PrayerCity("cairo", 30.0444, 31.2357),
        PrayerCity("alexandria", 31.2001, 29.9187),
        PrayerCity("dubai", 25.2048, 55.2708),
        PrayerCity("doha", 25.2854, 51.5310),
        PrayerCity("kuwait", 29.3759, 47.9774),
        PrayerCity("amman", 31.9539, 35.9106),
        PrayerCity("jerusalem", 31.7683, 35.2137),
        PrayerCity("istanbul", 41.0082, 28.9784),
    )

    fun matching(latitude: Double, longitude: Double): PrayerCity? {
        return all.firstOrNull { city ->
            abs(city.latitude - latitude) < 0.0002 && abs(city.longitude - longitude) < 0.0002
        }
    }
}
