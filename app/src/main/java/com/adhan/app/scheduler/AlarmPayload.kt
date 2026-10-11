package com.adhan.app.scheduler

import android.content.Context
import android.content.Intent
import com.adhan.app.adhanApp
import com.adhan.app.domain.Alert
import com.adhan.app.store.AlertJson
import org.json.JSONObject

/** Alert snapshot carried on the alarm PendingIntent. */
internal object AlarmPayload {
    const val EXTRA_ALERT_ID = "com.adhan.app.extra.ALERT_ID"
    const val EXTRA_ALERT_JSON = "com.adhan.app.extra.ALERT_JSON"

    fun write(intent: Intent, alert: Alert) {
        intent.putExtra(EXTRA_ALERT_ID, alert.id)
        intent.putExtra(EXTRA_ALERT_JSON, AlertJson.encode(alert).toString())
    }

    /**
     * Prefers the store so a later edit wins, then the intent snapshot
     * so a fire still plays if the process died before a flush.
     */
    fun read(context: Context, intent: Intent?): Alert? {
        if (intent == null) return null
        val id = intent.getStringExtra(EXTRA_ALERT_ID)
        if (!id.isNullOrBlank()) {
            context.adhanApp().alertStore.get(id)?.let { return it }
        }
        val json = intent.getStringExtra(EXTRA_ALERT_JSON) ?: return null
        return runCatching { AlertJson.decode(JSONObject(json)) }.getOrNull()
    }
}
