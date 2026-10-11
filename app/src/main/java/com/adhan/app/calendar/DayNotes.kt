package com.adhan.app.calendar

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

enum class DayNoteKind {
    ASHURA,
    WHITE_DAYS,
    ARAFAH,
    EID_FITR,
    EID_ADHA,
    NEW_YEAR,
    LUNAR_ECLIPSE,
    SOLAR_ECLIPSE,
}

data class DayNote(
    val id: String,
    val kind: DayNoteKind,
)

/**
 * One note for a civil date: a Hijri occasion, or a published eclipse.
 * Fasting days and the two Eids come from the Hijri date. Eclipse dates are
 * the NASA greatest-eclipse civil dates through 2028, not a local visibility model.
 */
object DayNotes {
    fun on(date: LocalDate): DayNote? {
        val hijriNote = hijri(date)
        val eclipseNote = eclipse(date)
        if (hijriNote != null && hijriNote.kind != DayNoteKind.WHITE_DAYS) return hijriNote
        return eclipseNote ?: hijriNote
    }

    private fun hijri(date: LocalDate): DayNote? {
        val hijri = HijrahDate.from(date)
        val month = hijri.get(ChronoField.MONTH_OF_YEAR)
        val day = hijri.get(ChronoField.DAY_OF_MONTH)
        val year = hijri.get(ChronoField.YEAR)
        return when {
            month == 1 && day == 10 -> DayNote("ashura-$year", DayNoteKind.ASHURA)
            month == 12 && day == 9 -> DayNote("arafah-$year", DayNoteKind.ARAFAH)
            month == 10 && day == 1 -> DayNote("eid-fitr-$year", DayNoteKind.EID_FITR)
            month == 12 && day == 10 -> DayNote("eid-adha-$year", DayNoteKind.EID_ADHA)
            month == 1 && day == 1 -> DayNote("new-year-$year", DayNoteKind.NEW_YEAR)
            day in 13..15 -> DayNote("white-$year-$month-$day", DayNoteKind.WHITE_DAYS)
            else -> null
        }
    }

    private fun eclipse(date: LocalDate): DayNote? {
        val kind = ECLIPSES[date] ?: return null
        val prefix = if (kind == DayNoteKind.LUNAR_ECLIPSE) "lunar" else "solar"
        return DayNote("$prefix-$date", kind)
    }

    private val ECLIPSES = mapOf(
        LocalDate.of(2026, 2, 17) to DayNoteKind.SOLAR_ECLIPSE,
        LocalDate.of(2026, 3, 3) to DayNoteKind.LUNAR_ECLIPSE,
        LocalDate.of(2026, 8, 12) to DayNoteKind.SOLAR_ECLIPSE,
        LocalDate.of(2026, 8, 28) to DayNoteKind.LUNAR_ECLIPSE,
        LocalDate.of(2027, 2, 6) to DayNoteKind.SOLAR_ECLIPSE,
        LocalDate.of(2027, 8, 2) to DayNoteKind.SOLAR_ECLIPSE,
        LocalDate.of(2028, 1, 12) to DayNoteKind.LUNAR_ECLIPSE,
        LocalDate.of(2028, 1, 26) to DayNoteKind.SOLAR_ECLIPSE,
        LocalDate.of(2028, 7, 6) to DayNoteKind.LUNAR_ECLIPSE,
        LocalDate.of(2028, 7, 22) to DayNoteKind.SOLAR_ECLIPSE,
        LocalDate.of(2028, 12, 31) to DayNoteKind.LUNAR_ECLIPSE,
    )
}
