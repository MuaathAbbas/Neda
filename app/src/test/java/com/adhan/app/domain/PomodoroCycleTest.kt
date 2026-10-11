package com.adhan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroCycleTest {
    @Test
    fun workThenBreakThenWork() {
        assertEquals(PomodoroPhase.BREAK, PomodoroCycle.other(PomodoroPhase.WORK))
        assertEquals(PomodoroPhase.WORK, PomodoroCycle.other(PomodoroPhase.BREAK))
    }

    @Test
    fun defaultLengthsAreTwentyFiveAndFive() {
        val start = 1_000_000L
        val workEnds = PomodoroCycle.endsAt(start, PomodoroPhase.WORK, 25, 5)
        val breakEnds = PomodoroCycle.endsAt(workEnds, PomodoroPhase.BREAK, 25, 5)
        assertEquals(25 * 60_000L, workEnds - start)
        assertEquals(5 * 60_000L, breakEnds - workEnds)
        assertEquals(PomodoroPhase.WORK, PomodoroCycle.other(PomodoroCycle.other(PomodoroPhase.WORK)))
    }

    @Test
    fun minutesStayInsideAWorkday() {
        assertEquals(1, PomodoroCycle.clamp(0))
        assertEquals(180, PomodoroCycle.clamp(500))
        assertEquals(40, PomodoroCycle.clamp(40))
    }
}
