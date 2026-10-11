package com.adhan.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * One reading of the device location, started only while the screen is asking
 * for it. This does not watch the user in the background and does not calculate
 * prayer times. The coordinates it returns are written into the place fields.
 */
class DeviceLocation(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)

    fun request(onResult: (Outcome) -> Unit): Cancel {
        val providers = enabledProviders()
        if (providers.isEmpty()) {
            onResult(Outcome.ServicesOff)
            return Cancel {}
        }
        if (!hasLocationPermission()) {
            onResult(Outcome.Missing)
            return Cancel {}
        }
        return subscribe(providers, onResult)
    }

    private fun enabledProviders(): List<String> {
        val names = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        return names.filter { name ->
            runCatching { manager.isProviderEnabled(name) }.getOrDefault(false)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun granted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun subscribe(providers: List<String>, onResult: (Outcome) -> Unit): Cancel {
        val handler = Handler(Looper.getMainLooper())
        val signal = CancellationSignal()
        var listener: LocationListener? = null
        var delivered = false

        fun stop() {
            signal.cancel()
            listener?.let { registered -> manager.removeUpdates(registered) }
            listener = null
            handler.removeCallbacksAndMessages(null)
        }

        fun deliver(outcome: Outcome) {
            if (delivered) return
            delivered = true
            stop()
            onResult(outcome)
        }

        val provider = providers.first()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.getCurrentLocation(
                provider,
                signal,
                ContextCompat.getMainExecutor(appContext),
            ) { location ->
                val fix = location ?: newestLastKnown(providers)
                if (fix == null) deliver(Outcome.Missing) else deliver(found(fix))
            }
        } else {
            val registered = LocationListener { location -> deliver(found(location)) }
            listener = registered
            manager.requestLocationUpdates(provider, 0L, 0f, registered, Looper.getMainLooper())
        }

        handler.postDelayed({
            val fix = newestLastKnown(providers)
            if (fix == null) deliver(Outcome.Missing) else deliver(found(fix))
        }, TIMEOUT_MS)

        return Cancel {
            delivered = true
            stop()
        }
    }

    @SuppressLint("MissingPermission")
    private fun newestLastKnown(providers: List<String>): Location? {
        return providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    private fun found(location: Location): Outcome.Found {
        val precise = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val stale = location.time <= 0L || System.currentTimeMillis() - location.time > STALE_MS
        val wide = !location.hasAccuracy() || location.accuracy > APPROXIMATE_METERS
        return Outcome.Found(
            latitude = location.latitude,
            longitude = location.longitude,
            approximate = !precise || stale || wide,
        )
    }

    fun interface Cancel {
        fun cancel()
    }

    sealed class Outcome {
        data class Found(
            val latitude: Double,
            val longitude: Double,
            val approximate: Boolean,
        ) : Outcome()

        data object ServicesOff : Outcome()

        data object Missing : Outcome()
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val STALE_MS = 10 * 60 * 1000L
        const val APPROXIMATE_METERS = 1_500f
    }
}
