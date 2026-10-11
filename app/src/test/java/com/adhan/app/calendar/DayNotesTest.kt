package com.adhan.app.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.chrono.HijrahDate

class DayNotesTest {
    @Test
    fun namesAshuraWhiteDaysArafahAndTheEids() {
        assertEquals(DayNoteKind.ASHURA, kindOnHijri(1, 10))
        assertEquals(DayNoteKind.WHITE_DAYS, kindOnHijri(9, 13))
        assertEquals(DayNoteKind.WHITE_DAYS, kindOnHijri(9, 15))
        assertEquals(DayNoteKind.ARAFAH, kindOnHijri(12, 9))
        assertEquals(DayNoteKind.EID_FITR, kindOnHijri(10, 1))
        assertEquals(DayNoteKind.EID_ADHA, kindOnHijri(12, 10))
        assertEquals(DayNoteKind.NEW_YEAR, kindOnHijri(1, 1))
        assertNull(DayNotes.on(LocalDate.from(HijrahDate.of(1447, 4, 8))))
    }

    @Test
    fun namesPublishedEclipses() {
        assertEquals(DayNoteKind.SOLAR_ECLIPSE, DayNotes.on(LocalDate.of(2027, 8, 2))?.kind)
        assertEquals(DayNoteKind.LUNAR_ECLIPSE, DayNotes.on(LocalDate.of(2026, 3, 3))?.kind)
        assertNull(DayNotes.on(LocalDate.of(2026, 3, 10)))
    }

    private fun kindOnHijri(month: Int, day: Int): DayNoteKind? {
        return DayNotes.on(LocalDate.from(HijrahDate.of(1448, month, day)))?.kind
    }
}
