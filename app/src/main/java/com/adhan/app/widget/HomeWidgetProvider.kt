package com.adhan.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class HomeWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TICK) {
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(component(context))
            if (ids.isEmpty()) {
                WidgetTicker.cancel(context)
            } else {
                ids.forEach { HomeWidgetRenderer.update(context, it) }
                WidgetTicker.schedule(context)
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { HomeWidgetRenderer.update(context, it) }
        if (appWidgetIds.isNotEmpty()) WidgetTicker.schedule(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.clear(context, it) }
    }

    override fun onDisabled(context: Context) {
        WidgetTicker.cancel(context)
    }

    companion object {
        const val ACTION_TICK = "com.adhan.app.action.WIDGET_TICK"

        fun component(context: Context) = ComponentName(context, HomeWidgetProvider::class.java)
    }
}
