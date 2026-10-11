package com.adhan.app.widget

import android.content.Context
import com.adhan.app.weather.WeatherLookup
import kotlin.math.abs

/** Keeps the last forecast so the home widget does not download every minute. */
internal object WidgetWeatherCache {
    private const val PREFS = "adhan_widget_weather"
    private const val MAX_AGE_MS = 3 * 60 * 60 * 1000L

    fun read(context: Context, latitude: Double, longitude: Double): WeatherLookup.Snapshot? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = prefs.getLong("at", 0L)
        if (System.currentTimeMillis() - at > MAX_AGE_MS) return null
        val savedLat = prefs.getFloat("lat", Float.NaN)
        val savedLon = prefs.getFloat("lon", Float.NaN)
        if (savedLat.isNaN() || abs(savedLat - latitude.toFloat()) > 0.05f) return null
        if (savedLon.isNaN() || abs(savedLon - longitude.toFloat()) > 0.05f) return null
        return WeatherLookup.Snapshot(prefs.getInt("celsius", 0), prefs.getInt("rain", 0))
    }

    fun write(context: Context, latitude: Double, longitude: Double, snapshot: WeatherLookup.Snapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("at", System.currentTimeMillis())
            .putFloat("lat", latitude.toFloat())
            .putFloat("lon", longitude.toFloat())
            .putInt("celsius", snapshot.celsius)
            .putInt("rain", snapshot.rainPercent)
            .apply()
    }
}
