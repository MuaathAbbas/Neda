package com.adhan.app.store

import com.adhan.app.domain.CoordinateText
import com.adhan.app.domain.Coordinates
import com.adhan.app.prayer.PrayerCalculationParameters
import com.adhan.app.prayer.PrayerMethods

/**
 * The place the user typed. Coordinates stay absent until both numbers parse
 * and fall inside the valid ranges. No city is filled in.
 */
data class PrayerPlace(
    val latitudeText: String = "",
    val longitudeText: String = "",
    val methodName: String = PrayerMethods.DEFAULT_METHOD,
    val madhabName: String = PrayerMethods.DEFAULT_MADHAB,
    val placeName: String = "",
) {
    fun coordinates(): Coordinates? {
        val latitude = CoordinateText.parse(latitudeText) ?: return null
        val longitude = CoordinateText.parse(longitudeText) ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return Coordinates(latitude, longitude)
    }

    fun parameters(): PrayerCalculationParameters {
        // Asr follows the source calendar (standard shadow). The stored madhab
        // is not applied, so a saved Hanafi choice cannot move Asr later.
        return PrayerCalculationParameters(methodName, PrayerMethods.DEFAULT_MADHAB)
    }
}
