package com.adhan.app.prayer

import com.adhan.app.domain.Alert
import com.adhan.app.domain.AlertType
import com.adhan.app.domain.Coordinates
import com.adhan.app.domain.Prayer
import com.adhan.app.domain.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PrayerOffsetTest {
    private val engine = AdhanPrayerEngine()
    private val makkah = Coordinates(21.4225, 39.8262)
    private val date = LocalDate.of(2026, 9, 26)
    private val ummAlQura = PrayerCalculationParameters("UMM_AL_QURA", "SHAFI")
    private val zone = ZoneId.of("Asia/Riyadh")

    @Test
    fun reminderAndIqamahSitAroundTheSameAdhan() {
        val fajr = instant(date, Prayer.FAJR)
        val alerts = plan(fajr - 30 * MINUTE, before = 10, iqamah = 15)
        assertEquals(fajr - 10 * MINUTE, trigger(alerts, Prayer.FAJR, PrayerAlerts.Role.BEFORE))
        assertEquals(fajr, trigger(alerts, Prayer.FAJR, PrayerAlerts.Role.ADHAN))
        assertEquals(fajr + 15 * MINUTE, trigger(alerts, Prayer.FAJR, PrayerAlerts.Role.IQAMAH))
        assertEquals(15, alerts.size)
    }

    @Test
    fun aPassedReminderMovesToTheNextDayWhileIqamahCanStillBeToday() {
        val fajr = instant(date, Prayer.FAJR)
        val tomorrowFajr = instant(date.plusDays(1), Prayer.FAJR)
        val between = plan(fajr - 5 * MINUTE, before = 10, iqamah = 15)
        assertEquals(tomorrowFajr - 10 * MINUTE, trigger(between, Prayer.FAJR, PrayerAlerts.Role.BEFORE))
        assertEquals(fajr + 15 * MINUTE, trigger(between, Prayer.FAJR, PrayerAlerts.Role.IQAMAH))

        val afterAdhan = plan(fajr + MINUTE, before = 10, iqamah = 15)
        assertEquals(tomorrowFajr - 10 * MINUTE, trigger(afterAdhan, Prayer.FAJR, PrayerAlerts.Role.BEFORE))
        assertEquals(fajr + 15 * MINUTE, trigger(afterAdhan, Prayer.FAJR, PrayerAlerts.Role.IQAMAH))
    }

    @Test
    fun aDisabledPrayerDisablesItsReminderAndIqamah() {
        val fajr = instant(date, Prayer.FAJR)
        val existing = Alert(
            id = PrayerAlerts.id(Prayer.FAJR),
            type = AlertType.PRAYER,
            schedule = Schedule.Prayer(Prayer.FAJR, fajr),
            enabled = false,
        )
        val alerts = PrayerAlerts.plan(
            engine,
            makkah,
            ummAlQura,
            fajr - MINUTE,
            zone,
            existing = listOf(existing),
            beforeMinutes = 10,
            iqamahMinutes = 10,
        )
        assertFalse(alerts.first { it.id == PrayerAlerts.id(Prayer.FAJR, PrayerAlerts.Role.BEFORE) }.enabled)
        assertFalse(alerts.first { it.id == PrayerAlerts.id(Prayer.FAJR, PrayerAlerts.Role.IQAMAH) }.enabled)
        assertTrue(alerts.first { it.id == PrayerAlerts.id(Prayer.DHUHR, PrayerAlerts.Role.BEFORE) }.enabled)
    }

    @Test
    fun zeroMinutesAddsNoExtraAlarms() {
        val alerts = plan(instant(date, Prayer.FAJR) - MINUTE, before = 0, iqamah = 0)
        assertEquals(PrayerAlerts.ringing, alerts.map { (it.schedule as Schedule.Prayer).prayer })
        assertNull(PrayerAlerts.parse("clock"))
        assertEquals(
            Prayer.MAGHRIB to PrayerAlerts.Role.IQAMAH,
            PrayerAlerts.parse(PrayerAlerts.id(Prayer.MAGHRIB, PrayerAlerts.Role.IQAMAH)),
        )
    }

    private fun plan(after: Long, before: Int, iqamah: Int): List<Alert> {
        return PrayerAlerts.plan(
            engine,
            makkah,
            ummAlQura,
            after,
            zone,
            beforeMinutes = before,
            iqamahMinutes = iqamah,
        )
    }

    private fun trigger(alerts: List<Alert>, prayer: Prayer, role: PrayerAlerts.Role): Long? {
        return alerts.first { it.id == PrayerAlerts.id(prayer, role) }.triggerAtEpochMillis
    }

    private fun instant(day: LocalDate, prayer: Prayer): Long {
        return engine.calculate(makkah, day, ummAlQura).times.first { it.prayer == prayer }.epochMillis
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
