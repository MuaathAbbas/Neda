package com.adhan.app.store

import android.content.Context
import com.adhan.app.domain.PomodoroCycle
import com.adhan.app.domain.PomodoroPhase

/** The focus loop the user started. Times are on the system clock. */
data class PomodoroSession(
    val workMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val running: Boolean = false,
    val phase: PomodoroPhase = PomodoroPhase.WORK,
    val endsAtEpochMillis: Long = 0L,
)

class PreferencesPomodoroStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): PomodoroSession {
        val phase = runCatching {
            PomodoroPhase.valueOf(prefs.getString(KEY_PHASE, null) ?: PomodoroPhase.WORK.name)
        }.getOrDefault(PomodoroPhase.WORK)
        return PomodoroSession(
            workMinutes = PomodoroCycle.clamp(prefs.getInt(KEY_WORK, 25)),
            breakMinutes = PomodoroCycle.clamp(prefs.getInt(KEY_BREAK, 5)),
            running = prefs.getBoolean(KEY_RUNNING, false),
            phase = phase,
            endsAtEpochMillis = prefs.getLong(KEY_ENDS, 0L),
        )
    }

    fun write(session: PomodoroSession) {
        prefs.edit()
            .putInt(KEY_WORK, PomodoroCycle.clamp(session.workMinutes))
            .putInt(KEY_BREAK, PomodoroCycle.clamp(session.breakMinutes))
            .putBoolean(KEY_RUNNING, session.running)
            .putString(KEY_PHASE, session.phase.name)
            .putLong(KEY_ENDS, session.endsAtEpochMillis)
            .commit()
    }

    private companion object {
        const val PREFS = "adhan_pomodoro"
        const val KEY_WORK = "work"
        const val KEY_BREAK = "break"
        const val KEY_RUNNING = "running"
        const val KEY_PHASE = "phase"
        const val KEY_ENDS = "ends"
    }
}
