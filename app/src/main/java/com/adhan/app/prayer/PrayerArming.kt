package com.adhan.app.prayer

import android.content.Context
import android.util.Log
import com.adhan.app.adhanApp
import com.adhan.app.domain.Alert
import com.adhan.app.domain.AlertType
import com.adhan.app.domain.Prayer
import com.adhan.app.domain.Schedule
import com.adhan.app.scheduler.PrayerDayRefresh
import com.adhan.app.store.PreferencesPrayerOffsetStore
import com.adhan.app.store.PreferencesPrayerPlaceStore
import com.adhan.app.store.PreferencesSoundChoiceStore
import com.adhan.app.store.SoundRole
import java.time.ZoneId

/**
 * Stores and arms prayer alerts. The activity sends a place; this object
 * asks [PrayerEngine] for the next five times and [com.adhan.app.scheduler.AlertScheduler]
 * to arm them. Callers do not compute prayer times.
 */
object PrayerArming {
    fun refreshStored(
        context: Context,
        nowEpochMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val app = context.adhanApp()
        val place = PreferencesPrayerPlaceStore(context).read()
        val coordinates = place.coordinates()
        if (coordinates == null) {
            clear(context)
            PrayerDayRefresh.cancel(context)
            return
        }
        try {
            val existing = prayerAlerts(app.alertStore.all())
            val offsets = PreferencesPrayerOffsetStore(context).read()
            val planned = PrayerAlerts.plan(
                engine = app.prayerEngine,
                coordinates = coordinates,
                parameters = place.parameters(),
                afterEpochMillis = nowEpochMillis,
                zone = zone,
                existing = existing,
                sound = PreferencesSoundChoiceStore(context).settings(SoundRole.PRAYER),
                beforeMinutes = offsets.beforeMinutes,
                iqamahMinutes = offsets.iqamahMinutes,
            )
            val plannedIds = planned.map { it.id }.toSet()
            existing.filter { it.id !in plannedIds }.forEach { alert ->
                app.alertScheduler.cancel(alert.id)
                app.alertStore.remove(alert.id)
            }
            planned.forEach { alert -> app.alertStore.put(alert) }
        } finally {
            PrayerDayRefresh.schedule(context, nowEpochMillis, zone)
        }
    }

    fun armStored(context: Context) {
        val app = context.adhanApp()
        val now = System.currentTimeMillis()
        prayerAlerts(app.alertStore.all()).forEach { alert ->
            val triggerAt = alert.triggerAtEpochMillis
            if (!alert.enabled || triggerAt == null || triggerAt <= now) {
                app.alertScheduler.cancel(alert.id)
                return@forEach
            }
            try {
                app.alertScheduler.schedule(alert)
            } catch (error: Exception) {
                Log.e(TAG, "Could not arm ${alert.id}", error)
                throw error
            }
        }
    }

    fun clear(context: Context) {
        val app = context.adhanApp()
        prayerAlerts(app.alertStore.all()).forEach { alert ->
            app.alertScheduler.cancel(alert.id)
            app.alertStore.remove(alert.id)
        }
    }

    fun setEnabled(context: Context, prayer: Prayer, enabled: Boolean) {
        val app = context.adhanApp()
        val ids = PrayerAlerts.Role.values().map { PrayerAlerts.id(prayer, it) }
        if (!enabled) {
            ids.forEach { id ->
                val current = app.alertStore.get(id) ?: return@forEach
                app.alertStore.put(current.copy(enabled = false))
                app.alertScheduler.cancel(id)
            }
            return
        }
        val place = PreferencesPrayerPlaceStore(context).read()
        val coordinates = place.coordinates() ?: return
        val offsets = PreferencesPrayerOffsetStore(context).read()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        ids.forEach { id ->
            val current = app.alertStore.get(id) ?: return@forEach
            val role = PrayerAlerts.parse(id)?.second ?: return@forEach
            val minutes = when (role) {
                PrayerAlerts.Role.ADHAN -> 0
                PrayerAlerts.Role.BEFORE -> offsets.beforeMinutes
                PrayerAlerts.Role.IQAMAH -> offsets.iqamahMinutes
            }
            if (role != PrayerAlerts.Role.ADHAN && minutes == 0) {
                app.alertScheduler.cancel(id)
                app.alertStore.remove(id)
                return@forEach
            }
            val schedule = current.schedule as Schedule.Prayer
            val trigger = if (schedule.triggerAtEpochMillis > now) {
                schedule.triggerAtEpochMillis
            } else {
                nextTrigger(
                    context,
                    prayer,
                    role,
                    offsets.beforeMinutes,
                    offsets.iqamahMinutes,
                    now,
                    zone,
                    coordinates,
                    place.parameters(),
                )
            }
            val updated = current.copy(
                enabled = true,
                schedule = schedule.copy(triggerAtEpochMillis = trigger),
            )
            app.alertStore.put(updated)
            app.alertScheduler.schedule(updated)
        }
    }

    /**
     * Moves one fired prayer to its next instant at the stored coordinates.
     * Returns the original alert when there is nothing to change.
     */
    fun afterFire(context: Context, alert: Alert, firedAtEpochMillis: Long): Alert {
        if (!alert.enabled) return alert
        val schedule = alert.schedule as? Schedule.Prayer ?: return alert
        val parsed = PrayerAlerts.parse(alert.id) ?: return alert
        val place = PreferencesPrayerPlaceStore(context).read()
        val coordinates = place.coordinates()
        if (coordinates == null) return alert.copy(enabled = false)
        val offsets = PreferencesPrayerOffsetStore(context).read()
        val minutes = when (parsed.second) {
            PrayerAlerts.Role.ADHAN -> 0
            PrayerAlerts.Role.BEFORE -> offsets.beforeMinutes
            PrayerAlerts.Role.IQAMAH -> offsets.iqamahMinutes
        }
        if (parsed.second != PrayerAlerts.Role.ADHAN && minutes == 0) {
            return alert.copy(enabled = false)
        }
        val next = nextTrigger(
            context,
            parsed.first,
            parsed.second,
            offsets.beforeMinutes,
            offsets.iqamahMinutes,
            maxOf(firedAtEpochMillis, schedule.triggerAtEpochMillis),
            ZoneId.systemDefault(),
            coordinates,
            place.parameters(),
        )
        return alert.copy(schedule = schedule.copy(triggerAtEpochMillis = next))
    }

    private fun nextTrigger(
        context: Context,
        prayer: Prayer,
        role: PrayerAlerts.Role,
        beforeMinutes: Int,
        iqamahMinutes: Int,
        afterEpochMillis: Long,
        zone: ZoneId,
        coordinates: com.adhan.app.domain.Coordinates,
        parameters: PrayerCalculationParameters,
    ): Long {
        val engine = context.adhanApp().prayerEngine
        if (role == PrayerAlerts.Role.ADHAN) {
            return PrayerAlerts.upcoming(
                engine,
                coordinates,
                parameters,
                prayer,
                afterEpochMillis,
                zone,
            )
        }
        val minutes = if (role == PrayerAlerts.Role.BEFORE) beforeMinutes else iqamahMinutes
        val offset = if (role == PrayerAlerts.Role.BEFORE) -minutes * 60_000L else minutes * 60_000L
        return PrayerAlerts.offsetTrigger(
            engine,
            coordinates,
            parameters,
            prayer,
            offset,
            afterEpochMillis,
            zone,
        )
    }

    private fun prayerAlerts(alerts: List<Alert>): List<Alert> {
        return alerts.filter { it.type == AlertType.PRAYER }
    }

    private const val TAG = "Adhan"
}
