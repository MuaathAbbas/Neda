package com.adhan.app.prayer

import com.adhan.app.domain.Alert
import com.adhan.app.domain.AlertType
import com.adhan.app.domain.Coordinates
import com.adhan.app.domain.Prayer
import com.adhan.app.domain.Schedule
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.batoulapps.adhan.Coordinates as AdhanCoordinates

class AdhanPrayerEngineTest {
    private val engine = AdhanPrayerEngine()
    private val makkah = Coordinates(21.4225, 39.8262)
    private val raleigh = Coordinates(35.78056, -78.6389)
    private val date = LocalDate.of(2026, 9, 26)
    private val ummAlQura = PrayerCalculationParameters("UMM_AL_QURA", "SHAFI")
    private val zone = ZoneId.of("Asia/Riyadh")

    @Test
    fun matchesLibraryOutputForTheCoordinatesItWasGiven() {
        val day = engine.calculate(makkah, date, ummAlQura)
        val direct = libraryTimes(makkah, date, "UMM_AL_QURA", "SHAFI")
        assertEquals(minute(direct.fajr.time), minute(instant(day, Prayer.FAJR)))
        assertEquals(minute(direct.sunrise.time), minute(instant(day, Prayer.SUNRISE)))
        assertEquals(minute(direct.dhuhr.time), minute(instant(day, Prayer.DHUHR)))
        assertEquals(minute(direct.asr.time), minute(instant(day, Prayer.ASR)))
        assertEquals(minute(direct.maghrib.time), minute(instant(day, Prayer.MAGHRIB)))
        assertEquals(minute(direct.isha.time), minute(instant(day, Prayer.ISHA)))
        assertEquals(0L, instant(day, Prayer.FAJR) % 60_000L)
        val again = engine.calculate(makkah, date, ummAlQura)
        assertEquals(instant(day, Prayer.FAJR), instant(again, Prayer.FAJR))
        assertEquals(date, Instant.ofEpochMilli(instant(day, Prayer.FAJR)).atZone(zone).toLocalDate())
    }

    @Test
    fun aSecondPlaceIsNotReplacedWithTheFirst() {
        val makkahFajr = instant(engine.calculate(makkah, date, ummAlQura), Prayer.FAJR)
        val raleighDay = engine.calculate(raleigh, date, ummAlQura)
        val raleighDirect = libraryTimes(raleigh, date, "UMM_AL_QURA", "SHAFI")
        assertEquals(minute(raleighDirect.fajr.time), minute(instant(raleighDay, Prayer.FAJR)))
        assertNotEquals(makkahFajr, instant(raleighDay, Prayer.FAJR))
    }

    @Test
    fun timesAreOrderedForMakkah() {
        val times = engine.calculate(makkah, date, ummAlQura).times.map { it.epochMillis }
        assertEquals(6, times.size)
        assertTrue(times.zipWithNext().all { (earlier, later) -> earlier < later })
    }

    @Test
    fun hanafiAsrIsLaterAndFajrStaysTheSame() {
        val shafi = engine.calculate(makkah, date, ummAlQura)
        val hanafi = engine.calculate(
            makkah,
            date,
            PrayerCalculationParameters("UMM_AL_QURA", "HANAFI"),
        )
        assertEquals(instant(shafi, Prayer.FAJR), instant(hanafi, Prayer.FAJR))
        assertTrue(instant(hanafi, Prayer.ASR) > instant(shafi, Prayer.ASR))
    }

    @Test
    fun egyptianMethodChangesFajrForTheSameCoordinates() {
        val ummAlQuraFajr = instant(engine.calculate(makkah, date, ummAlQura), Prayer.FAJR)
        val egyptianFajr = instant(
            engine.calculate(makkah, date, PrayerCalculationParameters("EGYPTIAN", "SHAFI")),
            Prayer.FAJR,
        )
        assertNotEquals(ummAlQuraFajr, egyptianFajr)
    }

    @Test
    fun aNamedCityIsOnlyItsCoordinates() {
        val cairo = PrayerCities.all.first { it.id == "cairo" }
        val fromCity = instant(
            engine.calculate(cairo.coordinates(), date, PrayerCalculationParameters("EGYPTIAN", "SHAFI")),
            Prayer.FAJR,
        )
        val fromNumbers = instant(
            engine.calculate(Coordinates(cairo.latitude, cairo.longitude), date, PrayerCalculationParameters("EGYPTIAN", "SHAFI")),
            Prayer.FAJR,
        )
        assertEquals(fromNumbers, fromCity)
        assertNotEquals(fromCity, instant(engine.calculate(makkah, date, PrayerCalculationParameters("EGYPTIAN", "SHAFI")), Prayer.FAJR))
    }

    @Test
    fun unknownMethodIsRejected() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            engine.calculate(makkah, date, PrayerCalculationParameters("UNSET", "SHAFI"))
        }
        assertTrue(error.message!!.contains("batoulapps"))
    }

    @Test
    fun fivePrayersAreScheduledAndSunriseIsNot() {
        val day = engine.calculate(makkah, date, ummAlQura)
        val beforeFajr = instant(day, Prayer.FAJR) - 1
        val alerts = PrayerAlerts.plan(engine, makkah, ummAlQura, beforeFajr, zone)
        assertEquals(PrayerAlerts.ringing, alerts.map { (it.schedule as Schedule.Prayer).prayer })
        assertEquals(instant(day, Prayer.FAJR), alerts.first().triggerAtEpochMillis)
        assertFalse(alerts.any { (it.schedule as Schedule.Prayer).prayer == Prayer.SUNRISE })
        assertEquals("prayer-FAJR", alerts.first().id)
    }

    @Test
    fun aFiredPrayerMovesToTheNextCivilDay() {
        val day = engine.calculate(makkah, date, ummAlQura)
        val fajr = instant(day, Prayer.FAJR)
        val rolled = PrayerAlerts.plan(engine, makkah, ummAlQura, fajr, zone)
        val tomorrow = engine.calculate(makkah, date.plusDays(1), ummAlQura)
        val rolledFajr = rolled.first { (it.schedule as Schedule.Prayer).prayer == Prayer.FAJR }
        assertEquals(instant(tomorrow, Prayer.FAJR), rolledFajr.triggerAtEpochMillis)
    }

    @Test
    fun planningKeepsADisabledPrayerDisabled() {
        val day = engine.calculate(makkah, date, ummAlQura)
        val existing = Alert(
            id = PrayerAlerts.id(Prayer.FAJR),
            type = AlertType.PRAYER,
            schedule = Schedule.Prayer(Prayer.FAJR, instant(day, Prayer.FAJR)),
            enabled = false,
        )
        val alerts = PrayerAlerts.plan(
            engine,
            makkah,
            ummAlQura,
            instant(day, Prayer.FAJR) - 1,
            zone,
            existing = listOf(existing),
        )
        assertFalse(alerts.first { it.id == PrayerAlerts.id(Prayer.FAJR) }.enabled)
        assertTrue(alerts.first { it.id == PrayerAlerts.id(Prayer.DHUHR) }.enabled)
    }

    private fun instant(day: PrayerDay, prayer: Prayer): Long {
        return day.times.first { it.prayer == prayer }.epochMillis
    }

    private fun minute(epochMillis: Long): Long = epochMillis / 60_000L

    private fun libraryTimes(
        coordinates: Coordinates,
        date: LocalDate,
        methodName: String,
        madhabName: String,
    ): PrayerTimes {
        val parameters = CalculationMethod.valueOf(methodName).parameters
        parameters.madhab = Madhab.valueOf(madhabName)
        return PrayerTimes(
            AdhanCoordinates(coordinates.latitude, coordinates.longitude),
            DateComponents(date.year, date.monthValue, date.dayOfMonth),
            parameters,
        )
    }
}
