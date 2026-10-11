package com.adhan.app.scheduler

import com.adhan.app.domain.Alert

/**
 * Arms wall-clock alerts. The Phase 1 implementation uses
 * `AlarmManager.setAlarmClock`. Place alerts are not scheduled here.
 *
 * Keep these methods stable. Later phases add behavior behind them
 * instead of replacing the contract.
 */
interface AlertScheduler {
    /**
     * Arm [alert]. Requires a TIME or PRAYER schedule and [Alert.enabled].
     * Throws [ExactAlarmNotPermittedException] when exact alarms are blocked.
     * Throws [IllegalArgumentException] for a place alert or a disabled alert.
     */
    fun schedule(alert: Alert)

    fun cancel(alertId: String)

    /**
     * Re-arm each enabled future TIME or PRAYER alert.
     * Past triggers are skipped. Used after boot.
     */
    fun rescheduleAll(alerts: List<Alert>)

    fun canScheduleExactAlarms(): Boolean
}

/** Exact alarms are unavailable until the user grants them. */
class ExactAlarmNotPermittedException :
    IllegalStateException("Exact alarm permission is not granted.")
