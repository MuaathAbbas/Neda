package com.adhan.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.adhan.app.AdhanApp
import com.adhan.app.R
import com.adhan.app.databinding.ActivityEditorBinding
import com.adhan.app.domain.Alert
import com.adhan.app.domain.AlertType
import com.adhan.app.domain.Schedule
import com.adhan.app.domain.TimeRepeat
import com.adhan.app.domain.TimeTriggers
import com.adhan.app.scheduler.ExactAlarmNotPermittedException
import com.adhan.app.store.PreferencesSoundChoiceStore
import com.adhan.app.store.SoundRole
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.UUID

/** Creates or edits one TIME alarm, then hands it to the store and scheduler. */
class AlarmEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditorBinding
    private val app: AdhanApp
        get() = application as AdhanApp

    private var alertId: String? = null
    private var existing: Alert? = null
    private var hour: Int = 6
    private var minute: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        alertId = intent.getStringExtra(EXTRA_ALERT_ID)
        val stored = alertId?.let { app.alertStore.get(it) }
        val storedTime = stored?.schedule as? Schedule.Time
        existing = if (storedTime != null) stored else null
        if (alertId != null && existing == null) {
            finish()
            return
        }

        if (savedInstanceState != null) {
            hour = savedInstanceState.getInt(STATE_HOUR)
            minute = savedInstanceState.getInt(STATE_MINUTE)
            binding.dailySwitch.isChecked = savedInstanceState.getBoolean(STATE_DAILY)
            binding.labelInput.setText(savedInstanceState.getString(STATE_LABEL).orEmpty())
        } else if (storedTime != null) {
            val zone = ZoneId.systemDefault()
            val local = java.time.Instant.ofEpochMilli(storedTime.triggerAtEpochMillis).atZone(zone)
            hour = storedTime.hour ?: local.hour
            minute = storedTime.minute ?: local.minute
            binding.dailySwitch.isChecked = storedTime.repeat == TimeRepeat.DAILY
            binding.labelInput.setText(stored?.label.orEmpty())
        } else {
            val nextHour = ZonedDateTime.now(ZoneId.systemDefault()).plusHours(1).withMinute(0).withSecond(0).withNano(0)
            hour = nextHour.hour
            minute = nextHour.minute
            binding.labelInput.setText(getString(R.string.default_alarm_label))
        }

        if (existing != null) {
            binding.editorTitle.setText(R.string.editor_title_edit)
        }
        renderTime()
        binding.timeButton.setOnClickListener { pickTime() }
        binding.saveButton.setOnClickListener { save() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_HOUR, hour)
        outState.putInt(STATE_MINUTE, minute)
        outState.putBoolean(STATE_DAILY, binding.dailySwitch.isChecked)
        outState.putString(STATE_LABEL, binding.labelInput.text?.toString().orEmpty())
    }

    private fun pickTime() {
        val format = if (DateFormat.is24HourFormat(this)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(format)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText(R.string.pick_time)
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .build()
        picker.addOnPositiveButtonClickListener {
            hour = picker.hour
            minute = picker.minute
            renderTime()
        }
        picker.show(supportFragmentManager, "alarm-time")
    }

    private fun renderTime() {
        val trigger = TimeTriggers.nextOccurrence(hour, minute, System.currentTimeMillis())
        binding.timeButton.text = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            .format(Date(trigger))
    }

    private fun save() {
        val now = System.currentTimeMillis()
        val trigger = TimeTriggers.nextOccurrence(hour, minute, now)
        val previous = existing
        val id = previous?.id ?: UUID.randomUUID().toString()
        val label = binding.labelInput.text?.toString()?.trim().orEmpty()
            .ifBlank { getString(R.string.default_alarm_label) }
        val alert = Alert(
            id = id,
            type = AlertType.TIME,
            schedule = Schedule.Time(
                triggerAtEpochMillis = trigger,
                repeat = if (binding.dailySwitch.isChecked) TimeRepeat.DAILY else TimeRepeat.ONCE,
                hour = hour,
                minute = minute,
            ),
            sound = previous?.sound ?: PreferencesSoundChoiceStore(this).settings(SoundRole.CLOCK),
            bypassDnd = previous?.bypassDnd ?: true,
            enabled = previous?.enabled ?: true,
            label = label,
        )
        app.alertStore.put(alert)
        try {
            if (alert.enabled) {
                app.alertScheduler.schedule(alert)
            } else {
                app.alertScheduler.cancel(alert.id)
            }
        } catch (error: ExactAlarmNotPermittedException) {
            restore(previous, id)
            showError(getString(R.string.status_exact_blocked))
            openExactAlarmSettings()
            return
        } catch (error: RuntimeException) {
            restore(previous, id)
            showError(getString(R.string.status_schedule_failed))
            return
        }
        val result = Intent()
        if (alert.enabled) {
            result.putExtra(EXTRA_SAVED_TRIGGER, trigger)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private fun restore(previous: Alert?, id: String) {
        if (previous == null) {
            app.alertStore.remove(id)
        } else {
            app.alertStore.put(previous)
        }
    }

    private fun showError(message: String) {
        binding.editorStatus.text = message
        binding.editorStatus.visibility = android.view.View.VISIBLE
    }

    private fun openExactAlarmSettings() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
        val settings = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = "package:$packageName".toUri()
        }
        runCatching { startActivity(settings) }
    }

    companion object {
        const val EXTRA_ALERT_ID = "com.adhan.app.extra.EDITOR_ALERT_ID"
        const val EXTRA_SAVED_TRIGGER = "com.adhan.app.extra.SAVED_TRIGGER"

        private const val STATE_HOUR = "hour"
        private const val STATE_MINUTE = "minute"
        private const val STATE_DAILY = "daily"
        private const val STATE_LABEL = "label"

        fun newIntent(context: Context, alertId: String?): Intent {
            return Intent(context, AlarmEditorActivity::class.java).apply {
                if (alertId != null) putExtra(EXTRA_ALERT_ID, alertId)
            }
        }
    }
}
