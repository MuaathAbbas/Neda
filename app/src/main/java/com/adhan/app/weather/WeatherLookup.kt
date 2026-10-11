package com.adhan.app.weather

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One temperature and one rain chance from the public forecast. No account. */
object WeatherLookup {
    data class Snapshot(val celsius: Int, val rainPercent: Int)

    fun fetch(latitude: Double, longitude: Double): Snapshot? {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m" +
            "&daily=precipitation_probability_max" +
            "&forecast_days=1&timezone=auto"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val celsius = json.getJSONObject("current").getDouble("temperature_2m")
            val rain = json.getJSONObject("daily")
                .getJSONArray("precipitation_probability_max")
                .getInt(0)
            Snapshot(celsius.toInt(), rain.coerceIn(0, 100))
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
