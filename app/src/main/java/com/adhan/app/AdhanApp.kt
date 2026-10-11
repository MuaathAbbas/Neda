package com.adhan.app

import android.app.Activity
import android.app.Application
import android.content.Context
import com.adhan.app.alert.AlarmAlertPlayer
import com.adhan.app.alert.AlarmChannels
import com.adhan.app.alert.AlertPlayer
import com.adhan.app.geofence.GeofenceEngine
import com.adhan.app.geofence.ProximityGeofenceEngine
import com.adhan.app.prayer.AdhanPrayerEngine
import com.adhan.app.prayer.PrayerEngine
import com.adhan.app.scheduler.AlarmManagerAlertScheduler
import com.adhan.app.scheduler.AlertScheduler
import com.adhan.app.store.AlertStore
import com.adhan.app.store.PreferencesAlertStore
import com.adhan.app.ui.MainActivity

class AdhanApp : Application() {
    lateinit var alertPlayer: AlertPlayer
        private set

    lateinit var alertScheduler: AlertScheduler
        private set

    lateinit var alertStore: AlertStore
        private set

    lateinit var prayerEngine: PrayerEngine
        private set

    lateinit var geofenceEngine: GeofenceEngine
        private set

    /** Screen opened from the alarm icon and the full-screen alarm notification. */
    val ringingActivity: Class<out Activity> = MainActivity::class.java

    override fun onCreate() {
        super.onCreate()
        AlarmChannels.ensureCreated(this)
        alertStore = PreferencesAlertStore(this)
        alertPlayer = AlarmAlertPlayer(this)
        alertScheduler = AlarmManagerAlertScheduler(this)
        prayerEngine = AdhanPrayerEngine()
        geofenceEngine = ProximityGeofenceEngine(this)
    }
}

fun Context.adhanApp(): AdhanApp = applicationContext as AdhanApp
