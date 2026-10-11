package com.adhan.app.store

import android.content.Context
import android.util.Log

/** Remembers the coordinates, method, and madhab the user saved. */
class PreferencesPrayerPlaceStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): PrayerPlace {
        return PrayerPlace(
            latitudeText = prefs.getString(KEY_LATITUDE, "").orEmpty(),
            longitudeText = prefs.getString(KEY_LONGITUDE, "").orEmpty(),
            methodName = prefs.getString(KEY_METHOD, null) ?: PrayerPlace().methodName,
            madhabName = prefs.getString(KEY_MADHAB, null) ?: PrayerPlace().madhabName,
            placeName = prefs.getString(KEY_NAME, "").orEmpty(),
        )
    }

    fun write(place: PrayerPlace) {
        val written = prefs.edit()
            .putString(KEY_LATITUDE, place.latitudeText)
            .putString(KEY_LONGITUDE, place.longitudeText)
            .putString(KEY_METHOD, place.methodName)
            .putString(KEY_MADHAB, place.madhabName)
            .putString(KEY_NAME, place.placeName)
            .commit()
        if (!written) {
            Log.e(TAG, "Failed to persist prayer place")
        }
    }

    private companion object {
        const val PREFS = "adhan_prayer_place"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_METHOD = "method"
        const val KEY_MADHAB = "madhab"
        const val KEY_NAME = "name"
        const val TAG = "Adhan"
    }
}
