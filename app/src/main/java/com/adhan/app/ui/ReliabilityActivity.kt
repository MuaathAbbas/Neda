package com.adhan.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.adhan.app.R
import com.adhan.app.adhanApp
import com.adhan.app.databinding.ActivityReliabilityBinding
import com.adhan.app.databinding.ItemReliabilityRowBinding
import com.adhan.app.domain.Alert
import com.adhan.app.domain.AlertType
import com.adhan.app.domain.NextAlarm
import com.adhan.app.domain.Schedule
import com.adhan.app.scheduler.ExactAlarmNotPermittedException
import com.adhan.app.store.PreferencesPrayerPlaceStore
import com.adhan.app.store.PreferencesSoundChoiceStore
import com.adhan.app.store.PrayerRefreshLog
import com.adhan.app.store.SoundRole
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Reads device state for the adhan path. It does not compute prayer times
 * or play audio. The test button stores a normal once-alarm and schedules it.
 */
class ReliabilityActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReliabilityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReliabilityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)
        binding.testAlarmButton.setOnClickListener { sendTestAlarm() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val app = adhanApp()
        bindNotifications()
        bindExact(app.alertScheduler.canScheduleExactAlarms())
        bindBattery()
        bindVolume(app.alertPlayer.isAlarmStreamMuted())
        bindDnd()
        bindFullscreen()
        bindPlace()
        bindNext(NextAlarm.soonest(app.alertStore.all(), System.currentTimeMillis()))
    }

    private fun bindNotifications() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val detail = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                getString(R.string.reliability_notifications_legacy)
            granted -> getString(R.string.reliability_notifications_ok)
            else -> getString(R.string.reliability_notifications_missing)
        }
        val level = if (granted) Level.OK else Level.BAD
        show(binding.rowNotifications, R.string.reliability_notifications, detail, level) {
            open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            })
        }
    }

    private fun bindExact(granted: Boolean) {
        val detail = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> getString(R.string.reliability_exact_legacy)
            granted -> getString(R.string.reliability_exact_ok)
            else -> getString(R.string.reliability_exact_missing)
        }
        val fix: (() -> Unit)? = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            null
        } else {
            {
                open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = "package:$packageName".toUri()
                })
            }
        }
        show(binding.rowExact, R.string.reliability_exact, detail, if (granted) Level.OK else Level.BAD, fix)
    }

    private fun bindBattery() {
        val unrestricted = getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(packageName)
        val detail = getString(
            if (unrestricted) R.string.reliability_battery_ok else R.string.reliability_battery_restricted,
        )
        show(
            binding.rowBattery,
            R.string.reliability_battery,
            detail,
            if (unrestricted) Level.OK else Level.WARN,
        ) {
            if (unrestricted) {
                open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                return@show
            }
            val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:$packageName".toUri()
            }
            if (!open(request)) {
                open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun bindVolume(muted: Boolean) {
        show(
            binding.rowVolume,
            R.string.reliability_volume,
            getString(if (muted) R.string.reliability_volume_muted else R.string.reliability_volume_ok),
            if (muted) Level.BAD else Level.OK,
        ) {
            open(Intent(Settings.ACTION_SOUND_SETTINGS))
        }
    }

    private fun bindDnd() {
        val granted = getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        show(
            binding.rowDnd,
            R.string.reliability_dnd,
            getString(if (granted) R.string.reliability_dnd_ok else R.string.reliability_dnd_missing),
            if (granted) Level.OK else Level.WARN,
        ) {
            open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    private fun bindFullscreen() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else {
            true
        }
        val detail = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                getString(R.string.reliability_fullscreen_legacy)
            granted -> getString(R.string.reliability_fullscreen_ok)
            else -> getString(R.string.reliability_fullscreen_missing)
        }
        val fix: (() -> Unit)? = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            null
        } else {
            {
                open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = "package:$packageName".toUri()
                })
            }
        }
        show(
            binding.rowFullscreen,
            R.string.reliability_fullscreen,
            detail,
            if (granted) Level.OK else Level.WARN,
            fix,
        )
    }

    private fun bindPlace() {
        val saved = PreferencesPrayerPlaceStore(this).read().coordinates() != null
        val refreshedAt = PrayerRefreshLog(this).read()
        val (detail, level) = when {
            !saved -> getString(R.string.reliability_place_missing) to Level.BAD
            refreshedAt == null -> getString(R.string.reliability_place_stale) to Level.WARN
            else -> getString(R.string.reliability_place_ok, formatWhen(refreshedAt)) to Level.OK
        }
        show(binding.rowPlace, R.string.reliability_place, detail, level) {
            openSection("settings")
        }
    }

    private fun bindNext(alert: Alert?) {
        val triggerAt = alert?.triggerAtEpochMillis
        if (alert == null || triggerAt == null) {
            show(
                binding.rowNext,
                R.string.reliability_next,
                getString(R.string.reliability_next_missing),
                Level.WARN,
            ) {
                openSection("prayer")
            }
            return
        }
        val name = alert.label.ifBlank { alert.id }
        show(
            binding.rowNext,
            R.string.reliability_next,
            getString(R.string.reliability_next_ok, name, formatWhen(triggerAt)),
            Level.OK,
        ) {
            openSection("prayer")
        }
    }

    private fun sendTestAlarm() {
        val app = adhanApp()
        if (!app.alertScheduler.canScheduleExactAlarms()) {
            showResult(getString(R.string.reliability_test_needs_exact), error = true)
            return
        }
        val triggerAt = System.currentTimeMillis() + 60_000L
        val alert = Alert(
            id = TEST_ALERT_ID,
            type = AlertType.TIME,
            schedule = Schedule.Time(triggerAt),
            sound = PreferencesSoundChoiceStore(this).settings(SoundRole.PRAYER),
            label = getString(R.string.reliability_test_label),
        )
        app.alertStore.put(alert)
        try {
            app.alertScheduler.schedule(alert)
        } catch (error: ExactAlarmNotPermittedException) {
            showResult(getString(R.string.reliability_test_needs_exact), error = true)
            return
        }
        showResult(getString(R.string.reliability_test_armed), error = false)
        render()
    }

    private fun show(
        row: ItemReliabilityRowBinding,
        title: Int,
        detail: String,
        level: Level,
        fix: (() -> Unit)?,
    ) {
        row.titleText.setText(title)
        row.detailText.text = detail
        val color = when (level) {
            Level.OK -> R.color.primary
            Level.WARN -> R.color.secondary
            Level.BAD -> R.color.error
        }
        row.statusMark.setTextColor(ContextCompat.getColor(this, color))
        row.fixButton.isVisible = fix != null
        row.fixButton.setOnClickListener { fix?.invoke() }
    }

    private fun openSection(section: String) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            putExtra(SECTION_EXTRA, section)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
    }

    private fun open(intent: Intent): Boolean {
        return runCatching { startActivity(intent) }.isSuccess
    }

    private fun showResult(message: String, error: Boolean) {
        binding.testResult.isVisible = true
        binding.testResult.text = message
        binding.testResult.setTextColor(
            ContextCompat.getColor(this, if (error) R.color.error else R.color.on_surface),
        )
    }

    private fun formatWhen(epochMillis: Long): String {
        val zoned = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .format(zoned)
    }

    private enum class Level { OK, WARN, BAD }

    private companion object {
        const val TEST_ALERT_ID = "reliability-test"
        const val SECTION_EXTRA = "com.adhan.app.extra.SECTION"
    }
}
