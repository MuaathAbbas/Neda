package com.adhan.app.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.adhan.app.databinding.ActivityWidgetConfigBinding
import com.adhan.app.widget.HomeWidgetRenderer
import com.adhan.app.widget.WidgetPrefs

/** Asks for a gallery photo and an optional note before the widget is placed. */
class WidgetConfigActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWidgetConfigBinding
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private val gallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return@registerForActivityResult
        WidgetPrefs.saveBackground(this, widgetId, uri)
        WidgetPrefs.background(this, widgetId)?.let { photo ->
            binding.widgetPreview.setImageBitmap(photo)
            binding.widgetPreview.scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)
        binding.widgetNoteInput.setText(WidgetPrefs.note(this, widgetId))
        binding.pickBackgroundButton.setOnClickListener { gallery.launch("image/*") }
        binding.saveWidgetButton.setOnClickListener { save() }
    }

    private fun save() {
        WidgetPrefs.setNote(this, widgetId, binding.widgetNoteInput.text?.toString().orEmpty())
        HomeWidgetRenderer.update(this, widgetId)
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        )
        finish()
    }
}
