package com.adhan.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlin.math.max

/** Background photo and optional note for one home-screen widget. */
internal object WidgetPrefs {
    fun note(context: Context, widgetId: Int): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(noteKey(widgetId), "")
            .orEmpty()
    }

    fun setNote(context: Context, widgetId: Int, note: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(noteKey(widgetId), note.trim())
            .apply()
    }

    fun saveBackground(context: Context, widgetId: Int, source: Uri) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val largest = max(bounds.outWidth, bounds.outHeight)
        while (largest / sample > 1280) sample *= 2
        val bitmap = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return
        backgroundFile(context, widgetId).outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        }
        bitmap.recycle()
    }

    fun background(context: Context, widgetId: Int): Bitmap? {
        val file = backgroundFile(context, widgetId)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        val largest = max(bounds.outWidth, bounds.outHeight)
        while (largest / sample > 400) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun clear(context: Context, widgetId: Int) {
        backgroundFile(context, widgetId).delete()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(noteKey(widgetId))
            .apply()
    }

    private fun backgroundFile(context: Context, widgetId: Int): File {
        val dir = File(context.filesDir, "widget").apply { mkdirs() }
        return File(dir, "$widgetId.jpg")
    }

    private fun noteKey(widgetId: Int) = "note_$widgetId"

    private const val PREFS = "adhan_widget"
}
