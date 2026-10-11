package com.adhan.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortServiceWindowTest {
    @Test
    fun untilStopExceedsTheShortServiceWindow() {
        assertTrue(ShortServiceWindow.exceedsWindow(0))
    }

    @Test
    fun choicesUpToTheLimitStayInsideTheWindow() {
        assertFalse(ShortServiceWindow.exceedsWindow(120))
        assertFalse(ShortServiceWindow.exceedsWindow(ShortServiceWindow.LIMIT_SECONDS))
    }

    @Test
    fun longerThanThreeMinutesExceedsTheWindow() {
        assertTrue(ShortServiceWindow.exceedsWindow(181))
        assertTrue(ShortServiceWindow.exceedsWindow(600))
    }

    @Test
    fun rejectsSecondsOutsideTheStoredRange() {
        assertThrows(IllegalArgumentException::class.java) {
            ShortServiceWindow.exceedsWindow(-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShortServiceWindow.exceedsWindow(601)
        }
    }
}
