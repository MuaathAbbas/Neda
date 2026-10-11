package com.adhan.app.domain

/** The next enabled clock or prayer alert. Place alerts have no trigger. */
object NextAlarm {
    fun soonest(alerts: List<Alert>, nowEpochMillis: Long): Alert? {
        return alerts
            .filter { alert ->
                alert.enabled && (alert.triggerAtEpochMillis?.let { it > nowEpochMillis } == true)
            }
            .minByOrNull { alert -> alert.triggerAtEpochMillis!! }
    }
}
