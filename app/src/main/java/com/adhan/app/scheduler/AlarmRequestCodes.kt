package com.adhan.app.scheduler

/** Stable PendingIntent request codes. The same id always maps to the same code. */
internal object AlarmRequestCodes {
    fun fire(alertId: String): Int = mix(alertId, 0x1000_0000)

    fun show(alertId: String): Int = mix(alertId, 0x2000_0000)

    fun notification(alertId: String): Int {
        val mixed = mix(alertId, 0x3000_0000)
        return if (mixed == 0) 1 else mixed
    }

    fun stop(alertId: String): Int = mix(alertId, 0x4000_0000)

    fun enter(alertId: String): Int = mix(alertId, 0x5000_0000)

    fun prayerDay(): Int = 0x5100_0001

    fun reminder(): Int = 0x5100_0002

    fun widgetTick(): Int = 0x5100_0003

    private fun mix(alertId: String, salt: Int): Int {
        var hash = salt.toLong() and 0xffff_ffffL
        alertId.forEach { char ->
            hash = (hash * 31L + char.code) and 0xffff_ffffL
        }
        return (hash and 0x7fff_ffffL).toInt()
    }
}
