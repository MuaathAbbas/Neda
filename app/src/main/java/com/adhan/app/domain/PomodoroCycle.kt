package com.adhan.app.domain

/** Work and rest alternate. The session keeps going until it is stopped. */
enum class PomodoroPhase {
    WORK,
    BREAK,
}

/**
 * Plain minute arithmetic for a focus loop.
 * It does not schedule alarms.
 */
object PomodoroCycle {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 180

    fun clamp(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)

    fun other(phase: PomodoroPhase): PomodoroPhase {
        return if (phase == PomodoroPhase.WORK) PomodoroPhase.BREAK else PomodoroPhase.WORK
    }

    fun durationMillis(phase: PomodoroPhase, workMinutes: Int, breakMinutes: Int): Long {
        val minutes = if (phase == PomodoroPhase.WORK) clamp(workMinutes) else clamp(breakMinutes)
        return minutes * 60_000L
    }

    fun endsAt(nowEpochMillis: Long, phase: PomodoroPhase, workMinutes: Int, breakMinutes: Int): Long {
        return nowEpochMillis + durationMillis(phase, workMinutes, breakMinutes)
    }
}
