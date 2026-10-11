package com.adhan.app.domain

/**
 * When an alert becomes due.
 *
 * [Time] and [Prayer] carry a wall-clock instant on the
 * `System.currentTimeMillis()` scale. The scheduler arms that instant.
 * It does not compute prayer times.
 *
 * [Place] is registered with the geofence engine. It has no clock instant.
 */
sealed class Schedule {
    /**
     * A clock alarm.
     *
     * [hour] and [minute] are the local wall time the user picked.
     * [triggerAtEpochMillis] is the next instant that wall time should fire.
     * Older stored alarms may have a trigger only; [hour] and [minute] are then null.
     * New fields stay at the end with defaults so existing call sites keep compiling.
     */
    data class Time(
        val triggerAtEpochMillis: Long,
        val repeat: TimeRepeat = TimeRepeat.ONCE,
        val hour: Int? = null,
        val minute: Int? = null,
    ) : Schedule() {
        init {
            val wallHour = hour
            val wallMinute = minute
            if (wallHour != null || wallMinute != null) {
                require(wallHour != null && wallMinute != null) { "Hour and minute must both be set." }
                require(wallHour in 0..23) { "Hour out of range: $wallHour" }
                require(wallMinute in 0..59) { "Minute out of range: $wallMinute" }
            }
            if (repeat == TimeRepeat.DAILY) {
                require(wallHour != null && wallMinute != null) {
                    "A daily alarm needs a local hour and minute."
                }
            }
        }
    }

    data class Prayer(
        val prayer: com.adhan.app.domain.Prayer,
        val triggerAtEpochMillis: Long,
    ) : Schedule()

    data class Place(
        val placeId: String,
        val coordinates: Coordinates,
        val radiusMeters: Float,
    ) : Schedule() {
        init {
            require(placeId.isNotBlank()) { "Place id must not be blank." }
            require(radiusMeters > 0f) { "Geofence radius must be positive." }
        }
    }
}
