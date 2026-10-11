package com.adhan.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.adhan.app.R
import com.adhan.app.adhanApp
import com.adhan.app.calendar.DayNoteKind
import com.adhan.app.calendar.DayNotes
import com.adhan.app.prayer.PrayerAlerts
import com.adhan.app.store.PreferencesPrayerPlaceStore
import com.adhan.app.ui.MainActivity
import com.adhan.app.ui.PrayerCopy
import com.adhan.app.weather.WeatherLookup
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

internal object HomeWidgetRenderer {
    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(HomeWidgetProvider.component(context))
        ids.forEach { update(context, it) }
        if (ids.isNotEmpty()) WidgetTicker.schedule(context)
    }

    fun update(context: Context, widgetId: Int) {
        val place = PreferencesPrayerPlaceStore(context).read()
        val coordinates = place.coordinates()
        val weather = coordinates?.let { WidgetWeatherCache.read(context, it.latitude, it.longitude) }
        publish(context, widgetId, weather)
        if (coordinates != null && weather == null) {
            thread(name = "widget-weather") {
                val snapshot = WeatherLookup.fetch(coordinates.latitude, coordinates.longitude) ?: return@thread
                WidgetWeatherCache.write(context, coordinates.latitude, coordinates.longitude, snapshot)
                publish(context, widgetId, snapshot)
            }
        }
    }

    private fun publish(context: Context, widgetId: Int, weather: WeatherLookup.Snapshot?) {
        val photo = WidgetPrefs.background(context, widgetId)
        val views = render(context, widgetId, weather, photo)
        AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views)
        photo?.recycle()
    }

    private fun render(
        context: Context,
        widgetId: Int,
        weather: WeatherLookup.Snapshot?,
        photo: android.graphics.Bitmap?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_home)
        val open = PendingIntent.getActivity(
            context,
            widgetId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, open)
        if (photo != null) views.setImageViewBitmap(R.id.widgetBackground, photo)
        views.setTextViewText(R.id.widgetHijri, hijriToday())
        bindPrayer(context, views)
        if (weather == null) {
            views.setViewVisibility(R.id.widgetWeather, View.GONE)
        } else {
            views.setViewVisibility(R.id.widgetWeather, View.VISIBLE)
            views.setTextViewText(
                R.id.widgetWeather,
                if (weather.rainPercent >= 20) {
                    context.getString(R.string.weather_line, weather.celsius, weather.rainPercent)
                } else {
                    context.getString(R.string.weather_dry, weather.celsius)
                },
            )
        }
        val note = displayNote(context, widgetId)
        if (note.isBlank()) {
            views.setViewVisibility(R.id.widgetNote, View.GONE)
        } else {
            views.setViewVisibility(R.id.widgetNote, View.VISIBLE)
            views.setTextViewText(R.id.widgetNote, note)
        }
        return views
    }

    private fun bindPrayer(context: Context, views: RemoteViews) {
        val place = PreferencesPrayerPlaceStore(context).read()
        val coordinates = place.coordinates()
        if (coordinates == null) {
            views.setTextViewText(R.id.widgetPrayer, context.getString(R.string.home_no_place))
            views.setTextViewText(R.id.widgetCountdown, "")
            return
        }
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val next = runCatching {
            PrayerAlerts.ringing.map { prayer ->
                prayer to PrayerAlerts.upcoming(
                    engine = context.adhanApp().prayerEngine,
                    coordinates = coordinates,
                    parameters = place.parameters(),
                    prayer = prayer,
                    afterEpochMillis = now,
                    zone = zone,
                )
            }.minBy { it.second }
        }.getOrNull()
        if (next == null) {
            views.setTextViewText(R.id.widgetPrayer, context.getString(R.string.prayer_calc_failed))
            views.setTextViewText(R.id.widgetCountdown, "")
            return
        }
        val left = (next.second - now).coerceAtLeast(0L)
        views.setTextViewText(
            R.id.widgetPrayer,
            context.getString(R.string.home_next, context.getString(PrayerCopy.prayerName(next.first))),
        )
        val countdown = if (left < 60_000L) {
            context.getString(R.string.widget_under_minute)
        } else {
            context.getString(
                R.string.widget_left,
                (left / 3_600_000L).toInt(),
                ((left / 60_000L) % 60L).toInt(),
            )
        }
        views.setTextViewText(R.id.widgetCountdown, countdown)
    }

    private fun displayNote(context: Context, widgetId: Int): String {
        val written = WidgetPrefs.note(context, widgetId)
        if (written.isNotBlank()) return written
        val kind = DayNotes.on(LocalDate.now())?.kind ?: return ""
        return context.getString(noteLabel(kind))
    }

    private fun noteLabel(kind: DayNoteKind): Int = when (kind) {
        DayNoteKind.ASHURA -> R.string.widget_note_ashura
        DayNoteKind.WHITE_DAYS -> R.string.widget_note_white
        DayNoteKind.ARAFAH -> R.string.widget_note_arafah
        DayNoteKind.EID_FITR -> R.string.widget_note_fitr
        DayNoteKind.EID_ADHA -> R.string.widget_note_adha
        DayNoteKind.NEW_YEAR -> R.string.widget_note_new_year
        DayNoteKind.LUNAR_ECLIPSE -> R.string.widget_note_lunar
        DayNoteKind.SOLAR_ECLIPSE -> R.string.widget_note_solar
    }

    private fun hijriToday(): String {
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy").withLocale(Locale.forLanguageTag("ar"))
        return HijrahDate.from(LocalDate.now()).format(formatter)
    }
}
