package com.adhan.app.geofence

import com.adhan.app.domain.Alert
import com.adhan.app.domain.AlertType
import com.adhan.app.domain.Schedule

/** Enabled place alerts are the only ones registered with the system. */
object PlaceRegistration {
    fun armed(alerts: List<Alert>): List<Alert> {
        return alerts.filter { alert ->
            alert.enabled && alert.type == AlertType.PLACE && alert.schedule is Schedule.Place
        }
    }
}

/** Avoids ringing again while the user is still inside the same circle. */
object PlaceEnterPolicy {
    const val PREFS = "adhan_place_entered"
    const val WINDOW_MS = 15 * 60 * 1000L

    fun allow(lastFiredAtEpochMillis: Long?, nowEpochMillis: Long): Boolean {
        val last = lastFiredAtEpochMillis ?: return true
        return nowEpochMillis - last >= WINDOW_MS
    }

    /** True when a known fix is already within the saved circle. */
    fun inside(distanceMeters: Float?, radiusMeters: Float): Boolean {
        val distance = distanceMeters ?: return false
        return distance >= 0f && distance <= radiusMeters
    }
}
