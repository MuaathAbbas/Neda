package com.adhan.app.store

/** Minutes before the adhan, and minutes after it for iqamah. Zero means off. */
data class PrayerOffsets(
    val beforeMinutes: Int = 0,
    val iqamahMinutes: Int = 0,
)
