package com.adhan.app.prayer

import com.adhan.app.domain.Coordinates
import com.adhan.app.domain.Prayer
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone
import com.batoulapps.adhan.Coordinates as AdhanCoordinates

/**
 * Prayer times from batoulapps adhan.
 *
 * [coordinates] is copied into the library as given. This class does not
 * keep a city, a last-known fix, or a fallback location.
 */
class AdhanPrayerEngine : PrayerEngine {
    override fun calculate(
        coordinates: Coordinates,
        date: LocalDate,
        parameters: PrayerCalculationParameters,
    ): PrayerDay {
        val libraryCoordinates = AdhanCoordinates(coordinates.latitude, coordinates.longitude)
        val libraryParameters = PrayerMethods.calculationMethod(parameters.methodName).parameters
        libraryParameters.madhab = PrayerMethods.madhab(parameters.madhabName)
        val components = DateComponents(date.year, date.monthValue, date.dayOfMonth)
        val times = PrayerTimes(libraryCoordinates, components, libraryParameters)
        return PrayerDay(
            date = date,
            times = listOf(
                instant(Prayer.FAJR, times.fajr, coordinates, date),
                instant(Prayer.SUNRISE, times.sunrise, coordinates, date),
                instant(Prayer.DHUHR, times.dhuhr, coordinates, date),
                instant(Prayer.ASR, times.asr, coordinates, date),
                instant(Prayer.MAGHRIB, times.maghrib, coordinates, date),
                instant(Prayer.ISHA, times.isha, coordinates, date),
            ),
        )
    }

    private fun instant(
        prayer: Prayer,
        date: Date?,
        coordinates: Coordinates,
        day: LocalDate,
    ): PrayerInstant {
        if (date == null) {
            throw PrayerTimesUnavailableException(
                "batoulapps adhan returned no $prayer time for " +
                    "${coordinates.latitude},${coordinates.longitude} on $day.",
            )
        }
        return PrayerInstant(prayer, minuteStart(date))
    }

    /**
     * batoulapps adhan rounds to the minute but leaves the calendar's
     * millisecond field untouched, so two calls differ by a few milliseconds.
     * Alarms use the start of that minute.
     */
    private fun minuteStart(date: Date): Long {
        val utc = GregorianCalendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.time = date
        utc.set(Calendar.MILLISECOND, 0)
        return utc.timeInMillis
    }
}
