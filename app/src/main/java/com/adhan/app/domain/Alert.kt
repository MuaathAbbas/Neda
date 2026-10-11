package com.adhan.app.domain

/**
 * One alarm, whatever caused it.
 *
 * Add new fields at the end, with defaults, so existing call sites keep compiling.
 * [type] and [schedule] must agree.
 */
data class Alert(
    val id: String,
    val type: AlertType,
    val schedule: Schedule,
    val sound: SoundSettings = SoundSettings(),
    val bypassDnd: Boolean = true,
    val enabled: Boolean = true,
    val label: String = "",
) {
    init {
        require(id.isNotBlank()) { "Alert id must not be blank." }
        when (type) {
            AlertType.TIME -> require(schedule is Schedule.Time) {
                "TIME alerts require Schedule.Time."
            }
            AlertType.PRAYER -> require(schedule is Schedule.Prayer) {
                "PRAYER alerts require Schedule.Prayer."
            }
            AlertType.PLACE -> require(schedule is Schedule.Place) {
                "PLACE alerts require Schedule.Place."
            }
        }
    }

    /** Wall-clock trigger, or null for a place alert. */
    val triggerAtEpochMillis: Long?
        get() = when (val schedule = schedule) {
            is Schedule.Time -> schedule.triggerAtEpochMillis
            is Schedule.Prayer -> schedule.triggerAtEpochMillis
            is Schedule.Place -> null
        }
}
