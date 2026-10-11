package com.adhan.app.prayer

import com.adhan.app.domain.Coordinates
import com.adhan.app.domain.Prayer
import java.time.LocalDate

/**
 * Names of a batoulapps adhan [com.batoulapps.adhan.CalculationMethod] and
 * [com.batoulapps.adhan.Madhab]. The engine maps these strings onto that
 * library. It does not invent angles.
 */
data class PrayerCalculationParameters(
    val methodName: String = "UNSET",
    val madhabName: String = "SHAFI",
)

data class PrayerInstant(
    val prayer: Prayer,
    val epochMillis: Long,
)

data class PrayerDay(
    val date: LocalDate,
    val times: List<PrayerInstant>,
)

/**
 * Prayer times for one place and one civil date.
 *
 * [coordinates] is the calculation input. Implementations must use that
 * argument as given. They must not replace it with a cached city, a stale
 * GPS fix, or a hard-coded location.
 *
 * Implementations delegate to the batoulapps adhan library. Do not add
 * hand-rolled astronomical formulas here.
 */
interface PrayerEngine {
    fun calculate(
        coordinates: Coordinates,
        date: LocalDate,
        parameters: PrayerCalculationParameters = PrayerCalculationParameters(),
    ): PrayerDay
}
