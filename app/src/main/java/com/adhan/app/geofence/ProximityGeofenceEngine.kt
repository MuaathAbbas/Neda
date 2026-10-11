package com.adhan.app.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import com.adhan.app.domain.Alert
import com.adhan.app.domain.AlertType
import com.adhan.app.domain.Schedule
import com.adhan.app.scheduler.AlarmPayload
import com.adhan.app.scheduler.AlarmRequestCodes

/**
 * Place alerts through [LocationManager.addProximityAlert].
 * No Google Play library, so it can run on phones that do not have Play services.
 * Entering the circle starts the same alarm playback path as a clock alarm.
 */
class ProximityGeofenceEngine(
    context: Context,
) : GeofenceEngine {
    private val appContext = context.applicationContext
    private val location = appContext.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    override fun add(alert: Alert) {
        val place = alert.schedule as? Schedule.Place
            ?: throw IllegalArgumentException("Only PLACE alerts are registered as geofences.")
        if (!alert.enabled || alert.type != AlertType.PLACE) {
            throw IllegalArgumentException("Refusing to register disabled alert ${alert.id}.")
        }
        suppressIfAlreadyInside(alert, place)
        location.addProximityAlert(
            place.coordinates.latitude,
            place.coordinates.longitude,
            place.radiusMeters,
            NO_EXPIRY,
            pending(alert),
        )
        Log.i(TAG, "proximity id=${alert.id} radius=${place.radiusMeters}")
    }

    override fun remove(alertId: String) {
        val pending = pending(alertId)
        location.removeProximityAlert(pending)
        pending.cancel()
        Log.i(TAG, "removed proximity id=$alertId")
    }

    override fun refresh(alerts: List<Alert>) {
        alerts.filter { it.type == AlertType.PLACE }.forEach { alert ->
            remove(alert.id)
        }
        PlaceRegistration.armed(alerts).forEach { alert ->
            try {
                add(alert)
            } catch (security: SecurityException) {
                Log.e(TAG, "Location permission blocked ${alert.id}", security)
            }
        }
    }

    private fun pending(alert: Alert): PendingIntent {
        val intent = Intent(appContext, PlaceEnteredReceiver::class.java).apply {
            action = PlaceEnteredReceiver.ACTION_ENTER
            AlarmPayload.write(this, alert)
        }
        return PendingIntent.getBroadcast(appContext, AlarmRequestCodes.enter(alert.id), intent, flags())
    }

    private fun pending(alertId: String): PendingIntent {
        val intent = Intent(appContext, PlaceEnteredReceiver::class.java).apply {
            action = PlaceEnteredReceiver.ACTION_ENTER
        }
        return PendingIntent.getBroadcast(appContext, AlarmRequestCodes.enter(alertId), intent, flags())
    }

    @SuppressLint("MissingPermission")
    private fun suppressIfAlreadyInside(alert: Alert, place: Schedule.Place) {
        val here = runCatching { newestLastKnown() }.getOrNull() ?: return
        val distance = FloatArray(1)
        Location.distanceBetween(
            here.latitude,
            here.longitude,
            place.coordinates.latitude,
            place.coordinates.longitude,
            distance,
        )
        if (!PlaceEnterPolicy.inside(distance[0], place.radiusMeters)) return
        appContext.getSharedPreferences(PlaceEnterPolicy.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(alert.id, System.currentTimeMillis())
            .commit()
        Log.i(TAG, "Already inside ${alert.id}; opening the app will not ring")
    }

    @SuppressLint("MissingPermission")
    private fun newestLastKnown(): Location? {
        val names = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        return names.mapNotNull { name ->
            runCatching { location.getLastKnownLocation(name) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    private fun flags(): Int {
        val base = PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The system adds KEY_PROXIMITY_ENTERING, so this intent must be mutable.
            base or PendingIntent.FLAG_MUTABLE
        } else {
            base
        }
    }

    private companion object {
        const val NO_EXPIRY = -1L
        const val TAG = "Adhan"
    }
}
