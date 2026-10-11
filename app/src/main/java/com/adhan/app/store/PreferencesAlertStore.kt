package com.adhan.app.store

import android.content.Context
import android.util.Log
import com.adhan.app.domain.Alert
import org.json.JSONArray

/**
 * Minimal durable store. Enough for the Phase 1 test alarm to survive
 * process death and be re-armed after boot.
 */
class PreferencesAlertStore(
    context: Context,
) : AlertStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    override fun get(id: String): Alert? = synchronized(lock) {
        readAll().find { it.id == id }
    }

    override fun put(alert: Alert) = synchronized(lock) {
        write(readAll().filterNot { it.id == alert.id } + alert)
    }

    override fun remove(id: String) = synchronized(lock) {
        write(readAll().filterNot { it.id == id })
    }

    override fun all(): List<Alert> = synchronized(lock) { readAll() }

    private fun readAll(): List<Alert> {
        val raw = prefs.getString(KEY_ALERTS, null) ?: return emptyList()
        val array = try {
            JSONArray(raw)
        } catch (error: Exception) {
            Log.w(TAG, "Ignoring corrupt alert store", error)
            return emptyList()
        }
        return buildList {
            for (index in 0 until array.length()) {
                try {
                    add(AlertJson.decode(array.getJSONObject(index)))
                } catch (error: Exception) {
                    Log.w(TAG, "Skipping corrupt alert at $index", error)
                }
            }
        }
    }

    private fun write(alerts: List<Alert>) {
        val array = JSONArray()
        alerts.forEach { alert -> array.put(AlertJson.encode(alert)) }
        val written = prefs.edit().putString(KEY_ALERTS, array.toString()).commit()
        if (!written) {
            Log.e(TAG, "Failed to persist alerts")
        }
    }

    private companion object {
        const val PREFS = "adhan_alerts"
        const val KEY_ALERTS = "alerts"
        const val TAG = "Adhan"
    }
}
