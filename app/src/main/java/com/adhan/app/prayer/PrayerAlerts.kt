package com.adhan.app.prayer

import com.adhan.app.domain.Alert
import com.adhan.app.domain.AlertType
import com.adhan.app.domain.Coordinates
import com.adhan.app.domain.Prayer
import com.adhan.app.domain.Schedule
import java.time.Instant
import java.time.ZoneId

/**
 * Builds the five adhan alerts for a place.
 *
 * Sunrise is calculated with the day and is not an alert.
 * The phone time zone chooses the civil date. [coordinates] are the only
 * location passed to [PrayerEngine.calculate].
 */
object PrayerAlerts {
    val ringing: List<Prayer> = listOf(
        Prayer.FAJR,
        Prayer.DHUHR,
        Prayer.ASR,
        Prayer.MAGHRIB,
        Prayer.ISHA,
    )

    enum class Role {
        ADHAN,
        BEFORE,
        IQAMAH,
    }

    fun id(prayer: Prayer, role: Role = Role.ADHAN): String = when (role) {
        Role.ADHAN -> "prayer-${prayer.name}"
        Role.BEFORE -> "prayer-before-${prayer.name}"
        Role.IQAMAH -> "prayer-iqamah-${prayer.name}"
    }

    fun parse(id: String): Pair<Prayer, Role>? {
        val prayer = Prayer.values().firstOrNull { candidate ->
            id == this.id(candidate) ||
                id == this.id(candidate, Role.BEFORE) ||
                id == this.id(candidate, Role.IQAMAH)
        } ?: return null
        val role = when (id) {
            this.id(prayer, Role.BEFORE) -> Role.BEFORE
            this.id(prayer, Role.IQAMAH) -> Role.IQAMAH
            else -> Role.ADHAN
        }
        return prayer to role
    }

    fun upcoming(
        engine: PrayerEngine,
        coordinates: Coordinates,
        parameters: PrayerCalculationParameters,
        prayer: Prayer,
        afterEpochMillis: Long,
        zone: ZoneId,
    ): Long {
        var date = Instant.ofEpochMilli(afterEpochMillis).atZone(zone).toLocalDate()
        repeat(3) {
            val instant = engine.calculate(coordinates, date, parameters)
                .times
                .first { it.prayer == prayer }
                .epochMillis
            if (instant > afterEpochMillis) return instant
            date = date.plusDays(1)
        }
        throw PrayerTimesUnavailableException(
            "No upcoming ${prayer.name} after $afterEpochMillis for " +
                "${coordinates.latitude},${coordinates.longitude}.",
        )
    }

    /** Next reminder or iqamah still ahead of [afterEpochMillis]. */
    fun offsetTrigger(
        engine: PrayerEngine,
        coordinates: Coordinates,
        parameters: PrayerCalculationParameters,
        prayer: Prayer,
        offsetMillis: Long,
        afterEpochMillis: Long,
        zone: ZoneId,
    ): Long {
        var date = Instant.ofEpochMilli(afterEpochMillis).atZone(zone).toLocalDate().minusDays(1)
        repeat(4) {
            val adhan = engine.calculate(coordinates, date, parameters)
                .times
                .first { it.prayer == prayer }
                .epochMillis
            val trigger = adhan + offsetMillis
            if (trigger > afterEpochMillis) return trigger
            date = date.plusDays(1)
        }
        throw PrayerTimesUnavailableException(
            "No upcoming offset for ${prayer.name} after $afterEpochMillis.",
        )
    }

    fun plan(
        engine: PrayerEngine,
        coordinates: Coordinates,
        parameters: PrayerCalculationParameters,
        afterEpochMillis: Long,
        zone: ZoneId,
        existing: List<Alert> = emptyList(),
        sound: com.adhan.app.domain.SoundSettings = com.adhan.app.domain.SoundSettings(),
        beforeMinutes: Int = 0,
        iqamahMinutes: Int = 0,
    ): List<Alert> {
        require(beforeMinutes in 0..180) { "Reminder minutes out of range: $beforeMinutes" }
        require(iqamahMinutes in 0..180) { "Iqamah minutes out of range: $iqamahMinutes" }
        val previous = existing.associateBy { it.id }
        return ringing.flatMap { prayer ->
            val kept = previous[id(prayer)]
            val enabled = kept?.enabled ?: true
            val adhanAt = upcoming(
                engine,
                coordinates,
                parameters,
                prayer,
                afterEpochMillis,
                zone,
            )
            val alerts = mutableListOf(
                prayerAlert(prayer, Role.ADHAN, adhanAt, sound, kept, enabled),
            )
            if (beforeMinutes > 0) {
                alerts += prayerAlert(
                    prayer,
                    Role.BEFORE,
                    offsetTrigger(
                        engine,
                        coordinates,
                        parameters,
                        prayer,
                        -beforeMinutes * 60_000L,
                        afterEpochMillis,
                        zone,
                    ),
                    sound,
                    kept,
                    enabled,
                )
            }
            if (iqamahMinutes > 0) {
                alerts += prayerAlert(
                    prayer,
                    Role.IQAMAH,
                    offsetTrigger(
                        engine,
                        coordinates,
                        parameters,
                        prayer,
                        iqamahMinutes * 60_000L,
                        afterEpochMillis,
                        zone,
                    ),
                    sound,
                    kept,
                    enabled,
                )
            }
            alerts
        }
    }

    private fun prayerAlert(
        prayer: Prayer,
        role: Role,
        triggerAtEpochMillis: Long,
        sound: com.adhan.app.domain.SoundSettings,
        kept: Alert?,
        enabled: Boolean,
    ): Alert {
        return Alert(
            id = id(prayer, role),
            type = AlertType.PRAYER,
            schedule = Schedule.Prayer(prayer = prayer, triggerAtEpochMillis = triggerAtEpochMillis),
            sound = sound,
            bypassDnd = kept?.bypassDnd ?: true,
            enabled = enabled,
            label = if (role == Role.ADHAN) kept?.label.orEmpty() else "",
        )
    }
}
