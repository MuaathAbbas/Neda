package com.adhan.app.geofence

import com.adhan.app.domain.Alert

/**
 * Place alerts. On enter, the implementation starts the same alarm playback
 * used by the clock. It does not calculate prayer times.
 */
interface GeofenceEngine {
    fun add(alert: Alert)

    fun remove(alertId: String)

    /** Replace registrations with the enabled place alerts in [alerts]. */
    fun refresh(alerts: List<Alert>)
}
