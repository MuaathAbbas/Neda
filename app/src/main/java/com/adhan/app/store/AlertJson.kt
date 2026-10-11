package com.adhan.app.store

import com.adhan.app.domain.Alert
import com.adhan.app.domain.AlertType
import com.adhan.app.domain.Coordinates
import com.adhan.app.domain.Prayer
import com.adhan.app.domain.Schedule
import com.adhan.app.domain.SoundSettings
import com.adhan.app.domain.TimeRepeat
import org.json.JSONObject

/**
 * Versioned alert codec shared by the preference store and alarm intents.
 * Unknown fields are ignored so a later version can add keys.
 */
object AlertJson {
    private const val VERSION = 1

    fun encode(alert: Alert): JSONObject {
        val json = JSONObject()
        json.put("v", VERSION)
        json.put("id", alert.id)
        json.put("type", alert.type.name)
        json.put("bypassDnd", alert.bypassDnd)
        json.put("enabled", alert.enabled)
        json.put("label", alert.label)
        json.put("vibrate", alert.sound.vibrate)
        json.put("loop", alert.sound.loop)
        json.put("ringSeconds", alert.sound.ringSeconds)
        json.put("fadeInSeconds", alert.sound.fadeInSeconds)
        if (alert.sound.soundUri != null) {
            json.put("soundUri", alert.sound.soundUri)
        }
        when (val schedule = alert.schedule) {
            is Schedule.Time -> {
                json.put("scheduleKind", "TIME")
                json.put("triggerAtEpochMillis", schedule.triggerAtEpochMillis)
                json.put("repeat", schedule.repeat.name)
                if (schedule.hour != null && schedule.minute != null) {
                    json.put("hour", schedule.hour)
                    json.put("minute", schedule.minute)
                }
            }
            is Schedule.Prayer -> {
                json.put("scheduleKind", "PRAYER")
                json.put("prayer", schedule.prayer.name)
                json.put("triggerAtEpochMillis", schedule.triggerAtEpochMillis)
            }
            is Schedule.Place -> {
                json.put("scheduleKind", "PLACE")
                json.put("placeId", schedule.placeId)
                json.put("latitude", schedule.coordinates.latitude)
                json.put("longitude", schedule.coordinates.longitude)
                json.put("radiusMeters", schedule.radiusMeters.toDouble())
            }
        }
        return json
    }

    fun decode(json: JSONObject): Alert {
        val type = AlertType.valueOf(json.getString("type"))
        val sound = SoundSettings(
            soundUri = json.optionalString("soundUri"),
            vibrate = if (json.has("vibrate")) json.getBoolean("vibrate") else true,
            loop = if (json.has("loop")) json.getBoolean("loop") else false,
            ringSeconds = if (json.has("ringSeconds")) json.getInt("ringSeconds").coerceIn(0, 600) else 0,
            fadeInSeconds = fadeInSeconds(json),
        )
        val schedule = when (val kind = json.getString("scheduleKind")) {
            "TIME" -> Schedule.Time(
                triggerAtEpochMillis = json.getLong("triggerAtEpochMillis"),
                repeat = if (json.has("repeat")) {
                    TimeRepeat.valueOf(json.getString("repeat"))
                } else {
                    TimeRepeat.ONCE
                },
                hour = json.optionalInt("hour"),
                minute = json.optionalInt("minute"),
            )
            "PRAYER" -> Schedule.Prayer(
                prayer = Prayer.valueOf(json.getString("prayer")),
                triggerAtEpochMillis = json.getLong("triggerAtEpochMillis"),
            )
            "PLACE" -> Schedule.Place(
                placeId = json.getString("placeId"),
                coordinates = Coordinates(
                    latitude = json.getDouble("latitude"),
                    longitude = json.getDouble("longitude"),
                ),
                radiusMeters = json.getDouble("radiusMeters").toFloat(),
            )
            else -> throw IllegalArgumentException("Unknown schedule kind $kind")
        }
        return Alert(
            id = json.getString("id"),
            type = type,
            schedule = schedule,
            sound = sound,
            bypassDnd = if (json.has("bypassDnd")) json.getBoolean("bypassDnd") else true,
            enabled = if (json.has("enabled")) json.getBoolean("enabled") else true,
            label = if (json.has("label") && !json.isNull("label")) json.getString("label") else "",
        )
    }

    private fun fadeInSeconds(json: JSONObject): Int {
        if (!json.has("fadeInSeconds") || json.isNull("fadeInSeconds")) return 0
        return when (val seconds = json.getInt("fadeInSeconds")) {
            4, 10 -> seconds
            else -> 0
        }
    }

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return getString(key)
    }

    private fun JSONObject.optionalInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return getInt(key)
    }
}
