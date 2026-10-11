package com.adhan.app.scheduler

import android.content.Context
import android.util.Log
import com.adhan.app.R
import com.adhan.app.adhanApp
import com.adhan.app.domain.Alert
import com.adhan.app.domain.AlertType
import com.adhan.app.domain.PomodoroCycle
import com.adhan.app.domain.PomodoroPhase
import com.adhan.app.domain.Schedule
import com.adhan.app.domain.TimeRepeat
import com.adhan.app.store.PomodoroSession
import com.adhan.app.store.PreferencesPomodoroStore
import com.adhan.app.store.PreferencesSoundChoiceStore
import com.adhan.app.store.SoundRole

/**
 * One clock alarm that hands itself the next phase.
 * Work ends, the break is armed, the break ends, work is armed again.
 */
object PomodoroArming {
    const val ALERT_ID = "pomodoro"

    fun start(context: Context, workMinutes: Int, breakMinutes: Int, nowEpochMillis: Long = System.currentTimeMillis()) {
        val session = PomodoroSession(
            workMinutes = PomodoroCycle.clamp(workMinutes),
            breakMinutes = PomodoroCycle.clamp(breakMinutes),
            running = true,
            phase = PomodoroPhase.WORK,
            endsAtEpochMillis = PomodoroCycle.endsAt(nowEpochMillis, PomodoroPhase.WORK, workMinutes, breakMinutes),
        )
        val store = PreferencesPomodoroStore(context)
        store.write(session)
        try {
            arm(context, session)
        } catch (error: Exception) {
            store.write(session.copy(running = false))
            context.adhanApp().alertStore.remove(ALERT_ID)
            throw error
        }
    }

    fun stop(context: Context) {
        val store = PreferencesPomodoroStore(context)
        val current = store.read()
        store.write(current.copy(running = false))
        val app = context.adhanApp()
        app.alertScheduler.cancel(ALERT_ID)
        app.alertStore.remove(ALERT_ID)
    }

    /** Called when the current phase alarm fires. Arms the other phase from now. */
    fun advance(context: Context, nowEpochMillis: Long = System.currentTimeMillis()) {
        val store = PreferencesPomodoroStore(context)
        val current = store.read()
        if (!current.running) {
            stop(context)
            return
        }
        val nextPhase = PomodoroCycle.other(current.phase)
        val session = current.copy(
            phase = nextPhase,
            endsAtEpochMillis = PomodoroCycle.endsAt(
                nowEpochMillis,
                nextPhase,
                current.workMinutes,
                current.breakMinutes,
            ),
        )
        store.write(session)
        try {
            arm(context, session)
        } catch (error: Exception) {
            Log.e(TAG, "Could not arm the next focus phase", error)
        }
    }

    /** Keeps a running session armed across reboot. A phase missed while off starts the next one. */
    fun rearm(context: Context, nowEpochMillis: Long = System.currentTimeMillis()) {
        val current = PreferencesPomodoroStore(context).read()
        if (!current.running) return
        try {
            if (current.endsAtEpochMillis > nowEpochMillis) {
                arm(context, current)
            } else {
                advance(context, nowEpochMillis)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Could not re-arm the focus loop", error)
        }
    }

    private fun arm(context: Context, session: PomodoroSession) {
        val app = context.adhanApp()
        val alert = alert(context, session)
        app.alertStore.put(alert)
        app.alertScheduler.schedule(alert)
    }

    private const val TAG = "Adhan"

    private fun alert(context: Context, session: PomodoroSession): Alert {
        val label = if (session.phase == PomodoroPhase.WORK) {
            context.getString(R.string.focus_alarm_work)
        } else {
            context.getString(R.string.focus_alarm_break)
        }
        return Alert(
            id = ALERT_ID,
            type = AlertType.TIME,
            schedule = Schedule.Time(
                triggerAtEpochMillis = session.endsAtEpochMillis,
                repeat = TimeRepeat.ONCE,
            ),
            sound = PreferencesSoundChoiceStore(context).settings(SoundRole.CLOCK),
            bypassDnd = true,
            enabled = true,
            label = label,
        )
    }
}
