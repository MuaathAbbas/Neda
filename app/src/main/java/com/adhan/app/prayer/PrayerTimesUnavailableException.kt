package com.adhan.app.prayer

/** The calculation library returned no usable instant for the requested place and date. */
class PrayerTimesUnavailableException(message: String) : IllegalStateException(message)
