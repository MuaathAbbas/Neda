package com.adhan.app.ui

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.adhan.app.AdhanApp
import com.adhan.app.R
import com.adhan.app.alert.AlarmChannels
import com.adhan.app.alert.AlarmLaunch
import com.adhan.app.alert.AlertPlaybackListener
import com.adhan.app.databinding.ActivityMainBinding
import com.adhan.app.databinding.ItemAlarmBinding
import com.adhan.app.databinding.ItemPrayerBinding
import com.adhan.app.domain.Alert
import com.adhan.app.domain.AlertType
import com.adhan.app.domain.CoordinateText
import com.adhan.app.domain.Prayer
import com.adhan.app.domain.Schedule
import com.adhan.app.domain.ShortServiceWindow
import com.adhan.app.domain.TimeRepeat
import com.adhan.app.domain.TimeTriggers
import com.adhan.app.location.DeviceLocation
import com.adhan.app.scheduler.ReminderAlarm
import com.adhan.app.widget.HomeWidgetProvider
import com.adhan.app.widget.HomeWidgetRenderer
import com.adhan.app.prayer.PrayerAlerts
import com.adhan.app.weather.WeatherLookup
import com.adhan.app.prayer.PrayerArming
import com.adhan.app.prayer.PrayerCities
import com.adhan.app.prayer.PrayerDay
import com.adhan.app.prayer.PrayerMethods
import com.adhan.app.domain.PomodoroPhase
import com.adhan.app.scheduler.ExactAlarmNotPermittedException
import com.adhan.app.scheduler.PomodoroArming
import com.adhan.app.store.PreferencesPomodoroStore
import com.adhan.app.store.PrayerPlace
import com.adhan.app.store.PreferencesPrayerOffsetStore
import com.adhan.app.store.PreferencesPrayerPlaceStore
import com.adhan.app.store.PreferencesSoundChoiceStore
import com.adhan.app.store.SoundRole
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread
import java.util.Date

/**
 * Alarm list and prayer place. Sends intents to [com.adhan.app.alert.AlertPlayer],
 * [com.adhan.app.scheduler.AlertScheduler], and [PrayerArming]. It does not play
 * audio itself and it does not compute prayer times.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val app: AdhanApp
        get() = application as AdhanApp

    private val placeStore by lazy { PreferencesPrayerPlaceStore(this) }
    private val offsetStore by lazy { PreferencesPrayerOffsetStore(this) }
    private var locationRequest: DeviceLocation.Cancel? = null

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { updatePermissionSummary() }

    private var fillPlaceFields = false
    private var pendingPlaceSave = false
    private var pendingSoundRole: SoundRole? = null
    private var applyingIcon = false
    private var expectRing = false
    private var shownPrayerDate: LocalDate? = null
    private var weatherPlace: String? = null

    private val focusTick = object : Runnable {
        override fun run() {
            if (!::binding.isInitialized) return
            renderFocus()
            renderHome()
            refreshPrayersForNewDay()
            binding.root.postDelayed(this, 1000L)
        }
    }

    private val soundPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val role = pendingSoundRole
        pendingSoundRole = null
        if (result.resultCode != RESULT_OK || role == null) return@registerForActivityResult
        val uri = pickedSound(result.data)
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val sounds = PreferencesSoundChoiceStore(this)
        sounds.write(role, uri?.toString())
        sounds.applyTo(role, app.alertStore)
        renderSoundNames()
        setStatus(getString(R.string.status_sound_saved))
        onTestSound(role)
    }

    private val backgroundLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { savePlaceNow() }

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val allowed = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (allowed) {
            if (pendingPlaceSave) {
                pendingPlaceSave = false
                if (fineLocationGranted()) {
                    onSavePlace()
                } else {
                    setStatus(getString(R.string.status_place_permission), error = true)
                }
            } else {
                readLocation()
            }
        } else {
            pendingPlaceSave = false
            setStatus(getString(R.string.status_location_denied), error = true)
            val askAgain = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (!askAgain) openAppSettings()
        }
    }

    private val editor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val trigger = result.data?.getLongExtra(AlarmEditorActivity.EXTRA_SAVED_TRIGGER, -1L) ?: -1L
        if (result.resultCode == RESULT_OK && trigger > 0L) {
            val clock = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(trigger))
            val message = savedMessage(clock)
            setStatus(message, error = app.alertPlayer.isAlarmStreamMuted())
        }
        renderAlarms()
    }

    private val playbackListener = AlertPlaybackListener {
        runOnUiThread {
            if (!::binding.isInitialized) return@runOnUiThread
            if (!app.alertPlayer.isPlaying()) {
                expectRing = false
                openedAlarmId = null
            }
            renderRinging()
            if (!app.alertPlayer.isPlaying()) {
                val status = binding.statusText.text?.toString().orEmpty()
                val transient = setOf(
                    getString(R.string.status_playing),
                    getString(R.string.status_ringing),
                )
                if (status in transient) {
                    setStatus(getString(R.string.status_idle))
                }
                renderAlarms()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.addButton.setOnClickListener {
            editor.launch(AlarmEditorActivity.newIntent(this, alertId = null))
        }
        binding.stopButton.setOnClickListener { stopRinging() }
        binding.ringingStopButton.setOnClickListener { stopRinging() }
        PreferencesSoundChoiceStore(this).ensureLooping(app.alertStore)
        binding.dndButton.setOnClickListener { openDndSettings() }
        binding.batteryButton.setOnClickListener { requestBatteryExemption() }
        binding.notificationsPromptButton.setOnClickListener { promptNotifications() }
        binding.exactPromptButton.setOnClickListener { openExactAlarmSettings() }
        binding.batteryPromptButton.setOnClickListener { requestBatteryExemption() }
        setupSections()
        setupSounds()
        setupFocus()
        setupPrayerChoices()
        setupOffsets()
        if (savedInstanceState == null) {
            showSavedPlace(placeStore.read())
        }
        binding.savePrayersButton.setOnClickListener { onSavePrayers() }
        binding.openSettingsButton.setOnClickListener {
            binding.sections.selectedItemId = R.id.section_settings
        }
        binding.pinWidgetButton.setOnClickListener { pinHomeWidget() }
        binding.reliabilityButton.setOnClickListener { openReliability() }
        binding.readinessCheckButton.setOnClickListener { openReliability() }
        binding.useLocationButton.setOnClickListener {
            fillPlaceFields = false
            onUseLocation()
        }
        binding.usePlaceLocationButton.setOnClickListener {
            fillPlaceFields = true
            onUseLocation()
        }
        binding.copyPrayerPlaceButton.setOnClickListener { copyPrayerCoordinates() }
        binding.savePlaceButton.setOnClickListener { onSavePlace() }
        setupPlaceRadius()
        consumeRingingIntent(intent)
        openRequestedSection(intent)
    }

    override fun onStart() {
        super.onStart()
        app.alertPlayer.addPlaybackFinishedListener(playbackListener)
        binding.stopButton.isEnabled = app.alertPlayer.isPlaying()
        binding.root.post(focusTick)
    }

    override fun onResume() {
        super.onResume()
        AlarmChannels.ensureCreated(this)
        updatePermissionSummary()
        maybePromptReadiness()
        runCatching { PomodoroArming.rearm(this) }
        ReminderAlarm.schedule(this)
        weatherPlace = null
        syncSavedPrayers()
        refreshPlaces()
        renderPrayers()
        renderHome()
        renderRinging()
        renderAlarms()
        renderPlaces()
        HomeWidgetRenderer.refreshAll(this)
    }

    override fun onStop() {
        if (::binding.isInitialized) {
            binding.root.removeCallbacks(focusTick)
            saveShownPrayerPlace(announce = false)
            binding.useLocationButton.isEnabled = true
            binding.usePlaceLocationButton.isEnabled = true
        }
        app.alertPlayer.removePlaybackFinishedListener(playbackListener)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeRingingIntent(intent)
        openRequestedSection(intent)
    }

    private fun openRequestedSection(source: Intent?) {
        when (source?.getStringExtra(SECTION_EXTRA)) {
            "settings" -> binding.sections.selectedItemId = R.id.section_settings
            "prayer" -> binding.sections.selectedItemId = R.id.section_prayer
        }
    }

    private fun pinHomeWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (!manager.isRequestPinAppWidgetSupported) {
            setStatus(getString(R.string.status_widget_manual), error = true)
            return
        }
        manager.requestPinAppWidget(HomeWidgetProvider.component(this), null, null)
    }

    private fun setupPrayerChoices() {
        bindDropdown(
            binding.methodInput,
            PrayerMethods.methods.map { getString(PrayerCopy.methodName(it)) },
        )
        val cities = PrayerCities.all
        bindDropdown(binding.cityInput, cities.map { getString(PrayerCopy.cityName(it.id)) })
        binding.cityInput.setOnItemClickListener { _, _, index, _ ->
            val city = cities[index]
            binding.latitudeInput.setText(CoordinateText.format(city.latitude))
            binding.longitudeInput.setText(CoordinateText.format(city.longitude))
            saveShownPrayerPlace(announce = true)
        }
    }

    private fun setupOffsets() {
        val labels = offsetChoices.map { offsetLabel(it) }
        bindDropdown(binding.beforeInput, labels)
        bindDropdown(binding.iqamahInput, labels)
        val saved = offsetStore.read()
        binding.beforeInput.setText(offsetLabel(saved.beforeMinutes), false)
        binding.iqamahInput.setText(offsetLabel(saved.iqamahMinutes), false)
        binding.beforeInput.setOnItemClickListener { _, _, _, _ -> saveOffsets() }
        binding.iqamahInput.setOnItemClickListener { _, _, _, _ -> saveOffsets() }
    }

    private fun saveOffsets() {
        offsetStore.write(
            com.adhan.app.store.PrayerOffsets(
                beforeMinutes = selectedOffset(binding.beforeInput),
                iqamahMinutes = selectedOffset(binding.iqamahInput),
            ),
        )
        if (placeStore.read().coordinates() == null) return
        try {
            PrayerArming.refreshStored(this)
            PrayerArming.armStored(this)
            setStatus(getString(R.string.status_offsets_saved))
        } catch (error: ExactAlarmNotPermittedException) {
            setStatus(getString(R.string.status_exact_blocked), error = true)
            openExactAlarmSettings()
        } catch (error: RuntimeException) {
            setStatus(getString(R.string.status_schedule_failed), error = true)
        }
        renderPrayers()
    }

    private fun offsetLabel(minutes: Int): String {
        val known = offsetChoices.firstOrNull { it == minutes } ?: 0
        return if (known == 0) getString(R.string.offset_off) else getString(R.string.offset_minutes, known)
    }

    private fun selectedOffset(field: android.widget.AutoCompleteTextView): Int {
        val text = field.text?.toString().orEmpty()
        return offsetChoices.firstOrNull { offsetLabel(it) == text } ?: 0
    }

    private fun setupPlaceRadius() {
        val labels = listOf(100, 200, 500).map { getString(R.string.radius_meters, it) }
        bindDropdown(binding.radiusInput, labels)
        if (binding.radiusInput.text.isNullOrBlank()) {
            binding.radiusInput.setText(getString(R.string.radius_meters, 200), false)
        }
    }

    private fun bindDropdown(
        field: android.widget.AutoCompleteTextView,
        labels: List<String>,
    ) {
        field.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels))
        field.setOnClickListener { field.showDropDown() }
        field.setOnFocusChangeListener { _, focused ->
            if (focused) field.showDropDown()
        }
    }

    private fun showSavedPlace(place: PrayerPlace) {
        binding.latitudeInput.setText(place.latitudeText)
        binding.longitudeInput.setText(place.longitudeText)
        val coordinates = place.coordinates()
        val city = if (coordinates == null) {
            null
        } else {
            PrayerCities.matching(coordinates.latitude, coordinates.longitude)
        }
        if (city != null) {
            binding.cityInput.setText(getString(PrayerCopy.cityName(city.id)), false)
        } else if (place.placeName.isNotBlank()) {
            binding.cityInput.setText(place.placeName, false)
        }
        val methodRes = PrayerCopy.methodName(place.methodName)
        if (methodRes != 0) binding.methodInput.setText(getString(methodRes), false)
    }

    private fun onUseLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            return
        }
        readLocation()
    }

    private fun readLocation() {
        locationRequest?.cancel()
        val button = if (fillPlaceFields) binding.usePlaceLocationButton else binding.useLocationButton
        button.isEnabled = false
        setStatus(getString(R.string.status_location_searching))
        val forPrayers = !fillPlaceFields
        locationRequest = DeviceLocation(this).request { outcome ->
            if (outcome is DeviceLocation.Outcome.Found && forPrayers) {
                savePrayerCoordinates(outcome.latitude, outcome.longitude)
                rememberPlaceName(outcome.latitude, outcome.longitude)
            }
            if (!::binding.isInitialized || isDestroyed) return@request
            button.isEnabled = true
            locationRequest = null
            when (outcome) {
                is DeviceLocation.Outcome.Found -> {
                    val latitude = if (fillPlaceFields) binding.placeLatitudeInput else binding.latitudeInput
                    val longitude = if (fillPlaceFields) binding.placeLongitudeInput else binding.longitudeInput
                    latitude.setText(CoordinateText.format(outcome.latitude))
                    longitude.setText(CoordinateText.format(outcome.longitude))
                    if (forPrayers) renderPrayers()
                    setStatus(
                        getString(
                            if (outcome.approximate) {
                                R.string.status_location_approximate
                            } else {
                                R.string.status_location_found
                            },
                        ),
                        error = outcome.approximate,
                    )
                }
                DeviceLocation.Outcome.ServicesOff -> {
                    setStatus(getString(R.string.status_location_disabled), error = true)
                    runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                }
                DeviceLocation.Outcome.Missing -> {
                    setStatus(getString(R.string.status_location_unavailable), error = true)
                }
            }
        }
    }

    private fun openAppSettings() {
        val settings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:$packageName".toUri()
        }
        runCatching { startActivity(settings) }
    }

    private fun onSavePrayers() {
        val latitudeText = binding.latitudeInput.text?.toString().orEmpty()
        val longitudeText = binding.longitudeInput.text?.toString().orEmpty()
        val method = selectedMethod()
        if (latitudeText.isBlank() && longitudeText.isBlank()) {
            placeStore.write(PrayerPlace(methodName = method, madhabName = PrayerMethods.DEFAULT_MADHAB))
            PrayerArming.clear(this)
            setStatus(getString(R.string.status_prayers_cleared))
            renderPrayers()
            return
        }
        if (!saveShownPrayerPlace(announce = true)) {
            setStatus(getString(R.string.status_coordinates_invalid), error = true)
        }
    }

    /**
     * Writes the coordinates currently on screen. Leaving the activity used to
     * drop them, because only the schedule button persisted the place.
     * Returns false when the fields are not a valid coordinate pair.
     */
    private fun saveShownPrayerPlace(announce: Boolean): Boolean {
        if (!::binding.isInitialized) return false
        val latitudeText = binding.latitudeInput.text?.toString().orEmpty().trim()
        val longitudeText = binding.longitudeInput.text?.toString().orEmpty().trim()
        if (latitudeText.isBlank() && longitudeText.isBlank()) return false
        val latitude = CoordinateText.parse(latitudeText)
        val longitude = CoordinateText.parse(longitudeText)
        if (latitude == null || longitude == null ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) {
            return false
        }
        placeStore.write(
            PrayerPlace(
                latitudeText = latitudeText,
                longitudeText = longitudeText,
                methodName = selectedMethod(),
                madhabName = PrayerMethods.DEFAULT_MADHAB,
                placeName = binding.cityInput.text?.toString()?.trim().orEmpty()
                    .ifBlank { placeStore.read().placeName },
            ),
        )
        return armSavedPrayers(announce)
    }

    private fun savePrayerCoordinates(latitude: Double, longitude: Double) {
        placeStore.write(
            PrayerPlace(
                latitudeText = CoordinateText.format(latitude),
                longitudeText = CoordinateText.format(longitude),
                methodName = if (::binding.isInitialized && !isDestroyed) {
                    selectedMethod()
                } else {
                    placeStore.read().methodName
                },
                madhabName = PrayerMethods.DEFAULT_MADHAB,
                placeName = placeStore.read().placeName,
            ),
        )
        armSavedPrayers(announce = false)
    }

    private fun armSavedPrayers(announce: Boolean): Boolean {
        try {
            PrayerArming.refreshStored(this)
            PrayerArming.armStored(this)
        } catch (error: ExactAlarmNotPermittedException) {
            if (announce) {
                setStatus(getString(R.string.status_exact_blocked), error = true)
                openExactAlarmSettings()
                renderPrayers()
            }
            return true
        } catch (error: RuntimeException) {
            if (announce) {
                setStatus(getString(R.string.status_prayer_calc_failed), error = true)
                renderPrayers()
            }
            return true
        }
        if (announce) {
            setStatus(getString(R.string.status_prayers_saved), error = app.alertPlayer.isAlarmStreamMuted())
            renderPrayers()
        }
        return true
    }

    private fun selectedMethod(): String {
        return selectedKey(
            binding.methodInput.text?.toString().orEmpty(),
            PrayerMethods.methods,
            PrayerCopy::methodName,
        ) ?: PrayerMethods.DEFAULT_METHOD
    }

    private fun selectedKey(label: String, keys: List<String>, labelRes: (String) -> Int): String? {
        return keys.firstOrNull { key ->
            val res = labelRes(key)
            res != 0 && getString(res) == label
        }
    }

    private fun refreshPrayersForNewDay() {
        val shown = shownPrayerDate ?: return
        if (shown == LocalDate.now()) return
        syncSavedPrayers()
        renderPrayers()
        renderHome()
    }

    private fun syncSavedPrayers() {
        if (placeStore.read().coordinates() == null) return
        try {
            PrayerArming.refreshStored(this)
            PrayerArming.armStored(this)
        } catch (error: Exception) {
            Log.w(TAG, "Prayer alarms were not re-armed", error)
        }
    }

    private fun renderPrayers() {
        val zone = ZoneId.systemDefault()
        shownPrayerDate = LocalDate.now(zone)
        val place = placeStore.read()
        val coordinates = place.coordinates()
        binding.prayerList.removeAllViews()
        if (coordinates == null) {
            binding.emptyPrayers.isVisible = true
            binding.emptyPrayers.setText(R.string.empty_prayers)
            binding.openSettingsButton.isVisible = true
            return
        }
        binding.openSettingsButton.isVisible = false
        val day = try {
            app.prayerEngine.calculate(coordinates, LocalDate.now(zone), place.parameters())
        } catch (error: RuntimeException) {
            binding.emptyPrayers.isVisible = true
            binding.emptyPrayers.setText(R.string.prayer_calc_failed)
            return
        }
        binding.emptyPrayers.isVisible = false
        val stored = app.alertStore.all().mapNotNull { alert ->
            val parsed = PrayerAlerts.parse(alert.id) ?: return@mapNotNull null
            if (parsed.second != PrayerAlerts.Role.ADHAN) return@mapNotNull null
            parsed.first to alert
        }.toMap()
        val now = System.currentTimeMillis()
        val order = listOf(
            Prayer.FAJR,
            Prayer.SUNRISE,
            Prayer.DHUHR,
            Prayer.ASR,
            Prayer.MAGHRIB,
            Prayer.ISHA,
        )
        val upcoming = order.firstOrNull { prayer ->
            day.times.first { it.prayer == prayer }.epochMillis > now
        }
        val inflater = LayoutInflater.from(this)
        order.forEach { prayer ->
            val row = ItemPrayerBinding.inflate(inflater, binding.prayerList, true)
            bindPrayer(row, prayer, day, stored[prayer], zone, prayer == upcoming)
        }
    }

    private fun bindPrayer(
        row: ItemPrayerBinding,
        prayer: Prayer,
        day: PrayerDay,
        stored: Alert?,
        zone: ZoneId,
        upcoming: Boolean,
    ) {
        val name = getString(PrayerCopy.prayerName(prayer))
        row.nameText.text = name
        val todayInstant = day.times.first { it.prayer == prayer }.epochMillis
        row.timeText.text = formatEpoch(todayInstant)
        val passed = todayInstant <= System.currentTimeMillis()
        row.nextBadge.isVisible = upcoming
        if (upcoming) {
            row.prayerCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.primary_container))
            row.prayerCard.strokeColor = ContextCompat.getColor(this, R.color.primary)
            row.prayerCard.strokeWidth = (2 * resources.displayMetrics.density).toInt()
            row.nameText.setTextColor(ContextCompat.getColor(this, R.color.on_primary_container))
        }
        if (prayer == Prayer.SUNRISE) {
            row.detailText.setText(R.string.prayer_not_alarm)
            row.enabledSwitch.isVisible = false
            row.timeText.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (upcoming && !passed) R.color.primary else if (passed) R.color.on_surface_muted else R.color.on_surface,
                ),
            )
            return
        }
        val trigger = stored?.triggerAtEpochMillis ?: todayInstant
        val enabled = stored?.enabled == true
        val quiet = passed || (stored != null && !enabled)
        row.timeText.setTextColor(
            ContextCompat.getColor(this, if (upcoming && !quiet) R.color.primary else if (quiet) R.color.on_surface_muted else R.color.on_surface),
        )
        val dayLabel = when {
            stored == null -> getString(R.string.prayer_not_scheduled)
            !enabled -> getString(R.string.repeat_off)
            else -> alarmDayLabel(trigger, zone)
        }
        row.detailText.text = if (enabled) offsetDetail(prayer, dayLabel) else dayLabel
        row.enabledSwitch.isVisible = stored != null
        row.enabledSwitch.setOnCheckedChangeListener(null)
        row.enabledSwitch.isChecked = enabled
        row.enabledSwitch.setOnCheckedChangeListener { _, checked ->
            if (stored != null && checked != stored.enabled) {
                togglePrayer(prayer, checked)
            }
        }
    }

    private fun offsetDetail(prayer: Prayer, dayLabel: String): String {
        val parts = mutableListOf(dayLabel)
        val before = app.alertStore.get(PrayerAlerts.id(prayer, PrayerAlerts.Role.BEFORE))
        val iqamah = app.alertStore.get(PrayerAlerts.id(prayer, PrayerAlerts.Role.IQAMAH))
        val beforeAt = before?.takeIf { it.enabled }?.triggerAtEpochMillis
        val iqamahAt = iqamah?.takeIf { it.enabled }?.triggerAtEpochMillis
        if (beforeAt != null) parts += getString(R.string.prayer_before_at, formatEpoch(beforeAt))
        if (iqamahAt != null) parts += getString(R.string.prayer_iqamah_at, formatEpoch(iqamahAt))
        return parts.joinToString(" · ")
    }

    private fun togglePrayer(prayer: Prayer, enabled: Boolean) {
        try {
            PrayerArming.setEnabled(this, prayer, enabled)
            setStatus(
                getString(if (enabled) R.string.status_prayers_saved else R.string.status_prayer_disabled),
            )
        } catch (error: ExactAlarmNotPermittedException) {
            setStatus(getString(R.string.status_exact_blocked), error = true)
            openExactAlarmSettings()
        } catch (error: RuntimeException) {
            setStatus(getString(R.string.status_schedule_failed), error = true)
        }
        renderPrayers()
    }

    private fun alarmDayLabel(triggerAtEpochMillis: Long, zone: ZoneId): String {
        val date = Instant.ofEpochMilli(triggerAtEpochMillis).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (date) {
            today -> getString(R.string.prayer_alarm_today)
            today.plusDays(1) -> getString(R.string.prayer_alarm_tomorrow)
            else -> getString(R.string.prayer_later)
        }
    }

    private fun formatEpoch(epochMillis: Long): String {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMillis))
    }

    private fun mutedIf(muted: Boolean): Int {
        return ContextCompat.getColor(this, if (muted) R.color.on_surface_muted else R.color.on_surface)
    }

    private fun renderAlarms() {
        val alarms = app.alertStore.all()
            .filter { it.schedule is Schedule.Time }
            .sortedWith(compareBy({ !it.enabled }, { minutesOfDay(it) }))
        binding.emptyAlarms.isVisible = alarms.isEmpty()
        binding.alarmList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        alarms.forEach { alert ->
            val row = ItemAlarmBinding.inflate(inflater, binding.alarmList, true)
            bindAlarm(row, alert)
        }
    }

    private fun bindAlarm(row: ItemAlarmBinding, alert: Alert) {
        val time = alert.schedule as Schedule.Time
        val color = ContextCompat.getColor(
            this,
            if (alert.enabled) R.color.on_surface else R.color.on_surface_muted,
        )
        row.timeText.text = formatClock(time)
        row.timeText.setTextColor(color)
        row.labelText.text = alert.label
        row.labelText.isVisible = alert.label.isNotBlank()
        row.repeatText.text = repeatLabel(alert, time)
        row.enabledSwitch.setOnCheckedChangeListener(null)
        row.enabledSwitch.isChecked = alert.enabled
        row.enabledSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != alert.enabled) {
                setEnabled(alert, checked)
            }
        }
        row.deleteButton.setOnClickListener { confirmDelete(alert) }
        row.alarmCard.setOnClickListener {
            editor.launch(AlarmEditorActivity.newIntent(this, alert.id))
        }
    }

    private fun setEnabled(alert: Alert, enabled: Boolean) {
        val time = alert.schedule as? Schedule.Time ?: return
        if (!enabled) {
            app.alertStore.put(alert.copy(enabled = false))
            app.alertScheduler.cancel(alert.id)
            setStatus(getString(R.string.status_disabled))
            renderAlarms()
            return
        }
        val hour = time.hour
        val minute = time.minute
        if (hour == null || minute == null) {
            setStatus(getString(R.string.status_missing_time), error = true)
            renderAlarms()
            return
        }
        val trigger = TimeTriggers.nextOccurrence(hour, minute, System.currentTimeMillis())
        val updated = alert.copy(
            enabled = true,
            schedule = time.copy(triggerAtEpochMillis = trigger),
        )
        app.alertStore.put(updated)
        try {
            app.alertScheduler.schedule(updated)
        } catch (error: ExactAlarmNotPermittedException) {
            app.alertStore.put(alert)
            setStatus(getString(R.string.status_exact_blocked), error = true)
            openExactAlarmSettings()
            renderAlarms()
            return
        } catch (error: RuntimeException) {
            app.alertStore.put(alert)
            setStatus(getString(R.string.status_schedule_failed), error = true)
            renderAlarms()
            return
        }
        val clock = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(trigger))
        setStatus(savedMessage(clock), error = app.alertPlayer.isAlarmStreamMuted())
        renderAlarms()
    }

    private fun confirmDelete(alert: Alert) {
        val fallback = when (val schedule = alert.schedule) {
            is Schedule.Time -> formatClock(schedule)
            is Schedule.Place -> getString(R.string.default_place_label)
            is Schedule.Prayer -> getString(R.string.default_place_label)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(alert.label.ifBlank { fallback })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> delete(alert) }
            .show()
    }

    private fun delete(alert: Alert) {
        app.alertScheduler.cancel(alert.id)
        if (alert.type == AlertType.PLACE) {
            app.geofenceEngine.remove(alert.id)
        }
        app.alertStore.remove(alert.id)
        if (app.alertPlayer.currentAlertId() == alert.id) {
            app.alertPlayer.stop()
        }
        setStatus(getString(R.string.status_deleted))
        renderAlarms()
        renderPlaces()
    }

    private fun copyPrayerCoordinates() {
        val latitude = binding.latitudeInput.text?.toString().orEmpty()
        val longitude = binding.longitudeInput.text?.toString().orEmpty()
        if (CoordinateText.parse(latitude) == null || CoordinateText.parse(longitude) == null) {
            setStatus(getString(R.string.status_place_needs_coordinates), error = true)
            return
        }
        binding.placeLatitudeInput.setText(latitude)
        binding.placeLongitudeInput.setText(longitude)
    }

    private fun onSavePlace() {
        if (!fineLocationGranted()) {
            fillPlaceFields = false
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            pendingPlaceSave = true
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !backgroundLocationGranted()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.place_background_title)
                .setMessage(R.string.place_background_message)
                .setPositiveButton(R.string.place_background_allow) { _, _ ->
                    backgroundLocationPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> savePlaceNow() }
                .show()
            return
        }
        savePlaceNow()
    }

    private fun savePlaceNow() {
        val latitude = CoordinateText.parse(binding.placeLatitudeInput.text?.toString().orEmpty())
        val longitude = CoordinateText.parse(binding.placeLongitudeInput.text?.toString().orEmpty())
        if (latitude == null || longitude == null ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) {
            setStatus(getString(R.string.status_coordinates_invalid), error = true)
            return
        }
        val label = binding.placeNameInput.text?.toString()?.trim().orEmpty()
            .ifBlank { getString(R.string.default_place_label) }
        val alert = Alert(
            id = java.util.UUID.randomUUID().toString(),
            type = AlertType.PLACE,
            schedule = Schedule.Place(
                placeId = label,
                coordinates = com.adhan.app.domain.Coordinates(latitude, longitude),
                radiusMeters = selectedRadius(),
            ),
            sound = PreferencesSoundChoiceStore(this).settings(SoundRole.PLACE),
            enabled = true,
            label = label,
        )
        app.alertStore.put(alert)
        try {
            app.geofenceEngine.add(alert)
        } catch (error: SecurityException) {
            setStatus(getString(R.string.status_place_permission), error = true)
            renderPlaces()
            return
        }
        binding.placeNameInput.setText("")
        setStatus(getString(R.string.status_place_saved, label))
        renderPlaces()
    }

    private fun selectedRadius(): Float {
        val digits = Regex("""\d+""").find(binding.radiusInput.text?.toString().orEmpty())?.value
        val meters = digits?.toIntOrNull() ?: 200
        return meters.coerceIn(50, 2000).toFloat()
    }

    private fun renderPlaces() {
        val places = app.alertStore.all()
            .filter { it.type == AlertType.PLACE }
            .sortedBy { it.label }
        binding.emptyPlaces.isVisible = places.isEmpty()
        binding.placeList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        places.forEach { alert ->
            val row = ItemAlarmBinding.inflate(inflater, binding.placeList, true)
            bindPlace(row, alert)
        }
    }

    private fun bindPlace(row: ItemAlarmBinding, alert: Alert) {
        val place = alert.schedule as Schedule.Place
        row.timeText.text = alert.label.ifBlank { getString(R.string.default_place_label) }
        row.timeText.setTextColor(mutedIf(!alert.enabled))
        row.labelText.isVisible = false
        row.repeatText.text = getString(R.string.place_radius_line, place.radiusMeters.toInt())
        row.alarmCard.setOnClickListener(null)
        row.alarmCard.isClickable = false
        row.enabledSwitch.setOnCheckedChangeListener(null)
        row.enabledSwitch.isChecked = alert.enabled
        row.enabledSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != alert.enabled) setPlaceEnabled(alert, checked)
        }
        row.deleteButton.setOnClickListener { confirmDelete(alert) }
    }

    private fun setPlaceEnabled(alert: Alert, enabled: Boolean) {
        val updated = alert.copy(enabled = enabled)
        app.alertStore.put(updated)
        if (!enabled) {
            app.geofenceEngine.remove(alert.id)
            setStatus(getString(R.string.status_disabled))
            renderPlaces()
            return
        }
        try {
            app.geofenceEngine.add(updated)
        } catch (error: SecurityException) {
            app.alertStore.put(alert)
            setStatus(getString(R.string.status_place_permission), error = true)
            renderPlaces()
            return
        }
        setStatus(getString(R.string.status_place_saved, updated.label))
        renderPlaces()
    }

    private fun refreshPlaces() {
        if (!fineLocationGranted()) return
        try {
            app.geofenceEngine.refresh(app.alertStore.all())
        } catch (error: Exception) {
            Log.w(TAG, "Places were not registered", error)
        }
    }

    private fun fineLocationGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun backgroundLocationGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun setupSections() {
        val pages = mapOf(
            R.id.section_prayer to binding.prayerPage,
            R.id.section_clock to binding.clockPage,
            R.id.section_place to binding.placePage,
            R.id.section_focus to binding.focusPage,
            R.id.section_settings to binding.settingsPage,
        )
        binding.sections.setOnItemSelectedListener { item ->
            pages.forEach { (id, page) -> page.isVisible = id == item.itemId }
            getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_SECTION, item.itemId)
                .apply()
            true
        }
        binding.sections.setOnItemReselectedListener { item ->
            pages[item.itemId]?.scrollTo(0, 0)
        }
        val saved = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
            .getInt(KEY_SECTION, R.id.section_prayer)
        if (pages.containsKey(saved)) binding.sections.selectedItemId = saved
    }

    private fun setupFocus() {
        val session = PreferencesPomodoroStore(this).read()
        binding.workMinutesInput.setText(session.workMinutes.toString())
        binding.breakMinutesInput.setText(session.breakMinutes.toString())
        binding.focusStartButton.setOnClickListener { startFocus() }
        binding.focusStopButton.setOnClickListener { stopFocus() }
        applyingIcon = true
        val selected = LauncherIcons.all.firstOrNull { it.id == LauncherIcons.selectedId(this) }
        if (selected != null) binding.iconGroup.check(selected.buttonId)
        applyingIcon = false
        binding.iconGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || applyingIcon) return@addOnButtonCheckedListener
            val option = LauncherIcons.all.firstOrNull { it.buttonId == checkedId } ?: return@addOnButtonCheckedListener
            LauncherIcons.apply(this, option.id)
            setStatus(getString(R.string.status_icon_changed))
        }
        renderFocus()
    }

    private fun startFocus() {
        val work = binding.workMinutesInput.text?.toString()?.toIntOrNull() ?: 25
        val rest = binding.breakMinutesInput.text?.toString()?.toIntOrNull() ?: 5
        try {
            PomodoroArming.start(this, work, rest)
            renderFocus()
            setStatus(getString(R.string.status_focus_started))
        } catch (error: ExactAlarmNotPermittedException) {
            setStatus(getString(R.string.status_exact_blocked), error = true)
            openExactAlarmSettings()
        } catch (error: RuntimeException) {
            setStatus(getString(R.string.status_schedule_failed), error = true)
        }
    }

    private fun stopFocus() {
        PomodoroArming.stop(this)
        renderFocus()
        setStatus(getString(R.string.status_focus_stopped))
    }

    private fun renderFocus() {
        val session = PreferencesPomodoroStore(this).read()
        val running = session.running
        binding.workMinutesInput.isEnabled = !running
        binding.breakMinutesInput.isEnabled = !running
        binding.focusStartButton.isEnabled = !running
        binding.focusStopButton.isEnabled = running
        if (!running) {
            binding.focusClock.text = getString(R.string.focus_clock, session.workMinutes, 0)
            binding.focusMessage.setText(R.string.focus_idle)
            return
        }
        val left = (session.endsAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val minutes = (left / 60_000L).toInt()
        val seconds = ((left / 1000L) % 60L).toInt()
        binding.focusClock.text = getString(R.string.focus_clock, minutes, seconds)
        binding.focusMessage.setText(
            if (session.phase == PomodoroPhase.WORK) R.string.focus_work else R.string.focus_break,
        )
    }

    private fun setupSounds() {
        val sounds = PreferencesSoundChoiceStore(this)
        binding.prayerSoundButton.setOnClickListener { pickSound(SoundRole.PRAYER) }
        binding.clockSoundButton.setOnClickListener { pickSound(SoundRole.CLOCK) }
        binding.placeSoundButton.setOnClickListener { pickSound(SoundRole.PLACE) }
        binding.builtinSoundButton.setOnClickListener {
            SoundRole.entries.forEach { role ->
                sounds.write(role, null)
                sounds.applyTo(role, app.alertStore)
            }
            renderSoundNames()
            setStatus(getString(R.string.status_sound_saved))
        }
        binding.testSoundButton.setOnClickListener { onTestSound(SoundRole.PRAYER) }
        binding.vibrateSwitch.isChecked = sounds.vibrateEnabled()
        binding.vibrateSwitch.setOnCheckedChangeListener { _, checked ->
            sounds.setVibrate(checked)
            SoundRole.entries.forEach { role -> sounds.applyTo(role, app.alertStore) }
        }
        val ringLabels = ringChoices.map { ringLabel(it) }
        bindDropdown(binding.ringSecondsInput, ringLabels)
        val savedRing = ringChoices.firstOrNull { it == sounds.ringSeconds() } ?: 0
        binding.ringSecondsInput.setText(ringLabel(savedRing), false)
        binding.ringSecondsInput.setOnItemClickListener { _, _, _, _ ->
            sounds.setRingSeconds(selectedRing(binding.ringSecondsInput))
            SoundRole.entries.forEach { role -> sounds.applyTo(role, app.alertStore) }
            updateRingWarning()
        }
        updateRingWarning()
        val fadeLabels = fadeChoices.map { fadeLabel(it) }
        bindDropdown(binding.fadeInInput, fadeLabels)
        val savedFade = fadeChoices.firstOrNull { it == sounds.fadeInSeconds() } ?: 0
        binding.fadeInInput.setText(fadeLabel(savedFade), false)
        binding.fadeInInput.setOnItemClickListener { _, _, _, _ ->
            sounds.setFadeInSeconds(selectedFade(binding.fadeInInput))
            SoundRole.entries.forEach { role -> sounds.applyTo(role, app.alertStore) }
        }
        renderSoundNames()
    }

    private fun updateRingWarning() {
        binding.ringLimitWarning.isVisible =
            ShortServiceWindow.exceedsWindow(selectedRing(binding.ringSecondsInput))
    }

    private fun fadeLabel(seconds: Int): String {
        return if (seconds == 0) {
            getString(R.string.fade_off)
        } else {
            getString(R.string.fade_seconds, seconds)
        }
    }

    private fun selectedFade(field: android.widget.AutoCompleteTextView): Int {
        val text = field.text?.toString().orEmpty()
        return fadeChoices.firstOrNull { fadeLabel(it) == text } ?: 0
    }

    private fun openReliability() {
        startActivity(Intent(this, ReliabilityActivity::class.java))
    }

    private fun ringLabel(seconds: Int): String {
        return if (seconds == 0) {
            getString(R.string.ring_until_stop)
        } else {
            getString(R.string.ring_seconds, seconds)
        }
    }

    private fun selectedRing(field: android.widget.AutoCompleteTextView): Int {
        val text = field.text?.toString().orEmpty()
        return ringChoices.firstOrNull { ringLabel(it) == text } ?: 0
    }

    private fun pickSound(role: SoundRole) {
        pendingSoundRole = role
        val existing = PreferencesSoundChoiceStore(this).read(role)?.toUri()
        val picker = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE or RingtoneManager.TYPE_NOTIFICATION,
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
        }
        soundPicker.launch(picker)
    }

    private fun pickedSound(data: Intent?): Uri? {
        if (data == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
    }

    private fun renderSoundNames() {
        val sounds = PreferencesSoundChoiceStore(this)
        binding.prayerSoundName.text = soundTitle(sounds.read(SoundRole.PRAYER))
        binding.clockSoundName.text = soundTitle(sounds.read(SoundRole.CLOCK))
        binding.placeSoundName.text = soundTitle(sounds.read(SoundRole.PLACE))
    }

    private fun soundTitle(uri: String?): String {
        if (uri.isNullOrBlank()) return getString(R.string.sound_default)
        val title = runCatching { RingtoneManager.getRingtone(this, uri.toUri())?.getTitle(this) }.getOrNull()
        return title?.takeIf { it.isNotBlank() } ?: getString(R.string.sound_custom)
    }

    private fun onTestSound(role: SoundRole) {
        val alert = Alert(
            id = PREVIEW_ALERT_ID,
            type = AlertType.TIME,
            schedule = Schedule.Time(System.currentTimeMillis()),
            sound = PreferencesSoundChoiceStore(this).let { sounds ->
                sounds.settings(role, loop = sounds.ringSeconds() > 0)
            },
            bypassDnd = true,
            enabled = true,
            label = getString(R.string.preview_label),
        )
        try {
            app.alertPlayer.play(alert)
            binding.stopButton.isEnabled = true
            if (app.alertPlayer.isAlarmStreamMuted()) {
                setStatus(getString(R.string.status_alarm_volume_muted), error = true)
            } else {
                setStatus(getString(R.string.status_playing))
            }
        } catch (error: Exception) {
            setStatus(getString(R.string.status_play_failed), error = true)
        }
    }

    private fun repeatLabel(alert: Alert, time: Schedule.Time): String {
        if (!alert.enabled) return getString(R.string.repeat_off)
        val zone = ZoneId.systemDefault()
        val triggerDate = Instant.ofEpochMilli(time.triggerAtEpochMillis).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (time.repeat) {
            TimeRepeat.DAILY -> getString(R.string.repeat_daily)
            TimeRepeat.ONCE -> when (triggerDate) {
                today -> getString(R.string.repeat_once_today)
                today.plusDays(1) -> getString(R.string.repeat_once_tomorrow)
                else -> getString(R.string.repeat_once)
            }
        }
    }

    private fun formatClock(time: Schedule.Time): String {
        val hour = time.hour
        val minute = time.minute
        val instant = if (hour != null && minute != null) {
            TimeTriggers.nextOccurrence(hour, minute, System.currentTimeMillis() - 1)
        } else {
            time.triggerAtEpochMillis
        }
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(instant))
    }

    private fun minutesOfDay(alert: Alert): Int {
        val time = alert.schedule as Schedule.Time
        val hour = time.hour
        val minute = time.minute
        return if (hour != null && minute != null) hour * 60 + minute else 0
    }

    private fun savedMessage(clock: String): String {
        val saved = getString(R.string.status_saved, clock)
        return if (app.alertPlayer.isAlarmStreamMuted()) {
            "$saved ${getString(R.string.status_alarm_volume_muted)}"
        } else {
            saved
        }
    }

    private fun consumeRingingIntent(source: Intent?) {
        if (source?.getBooleanExtra(AlarmLaunch.EXTRA_RINGING, false) != true) return
        openedAlarmId = source.getStringExtra(ALARM_ID_EXTRA)
        source.removeExtra(AlarmLaunch.EXTRA_RINGING)
        setIntent(source)
        expectRing = true
        wakeForAlarm()
        renderRinging()
        setStatus(getString(R.string.status_ringing))
    }

    private fun stopRinging() {
        expectRing = false
        openedAlarmId = null
        app.alertPlayer.stop()
        renderRinging()
    }

    private fun renderRinging() {
        val playing = app.alertPlayer.isPlaying()
        val visible = playing || expectRing
        binding.ringingCard.isVisible = visible
        binding.stopButton.isVisible = visible
        binding.stopButton.isEnabled = visible
        if (!visible) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            return
        }
        val id = app.alertPlayer.currentAlertId() ?: openedAlarmId
        val alert = id?.let { app.alertStore.get(it) }
        binding.ringingTitle.text = if (alert != null) ringingName(alert) else getString(R.string.app_name)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun ringingName(alert: Alert): String {
        val parsed = PrayerAlerts.parse(alert.id)
        if (parsed != null) {
            val name = getString(PrayerCopy.prayerName(parsed.first))
            return when (parsed.second) {
                PrayerAlerts.Role.ADHAN -> name
                PrayerAlerts.Role.BEFORE -> getString(R.string.alarm_before, name)
                PrayerAlerts.Role.IQAMAH -> getString(R.string.alarm_iqamah, name)
            }
        }
        return alert.label.ifBlank { getString(R.string.app_name) }
    }

    private fun renderHome() {
        binding.hijriText.text = hijriToday()
        val place = placeStore.read()
        val placeName = place.placeName.trim()
        binding.placeSummaryText.isVisible = placeName.isNotBlank()
        binding.placeSummaryText.text = placeName
        val coordinates = place.coordinates()
        if (coordinates == null) {
            binding.nextPrayerTitle.setText(R.string.home_no_place)
            binding.nextPrayerCountdown.text = ""
            binding.weatherText.isVisible = false
            return
        }
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val next = runCatching {
            PrayerAlerts.ringing.map { prayer ->
                prayer to PrayerAlerts.upcoming(
                    engine = app.prayerEngine,
                    coordinates = coordinates,
                    parameters = place.parameters(),
                    prayer = prayer,
                    afterEpochMillis = now,
                    zone = zone,
                )
            }.minBy { it.second }
        }.getOrNull()
        if (next == null) {
            binding.nextPrayerTitle.setText(R.string.home_no_place)
            binding.nextPrayerCountdown.text = ""
            return
        }
        val name = getString(PrayerCopy.prayerName(next.first))
        binding.nextPrayerTitle.text = getString(R.string.home_next, name)
        val left = (next.second - now).coerceAtLeast(0L)
        val hours = (left / 3_600_000L).toInt()
        val minutes = ((left / 60_000L) % 60L).toInt()
        val seconds = ((left / 1000L) % 60L).toInt()
        binding.nextPrayerCountdown.text = getString(R.string.home_countdown, hours, minutes, seconds)
        showWeather(coordinates.latitude, coordinates.longitude)
    }

    private fun showWeather(latitude: Double, longitude: Double) {
        val key = "%.2f,%.2f".format(Locale.US, latitude, longitude)
        if (weatherPlace == key) return
        weatherPlace = key
        thread(name = "weather") {
            val snapshot = WeatherLookup.fetch(latitude, longitude)
            runOnUiThread {
                if (isDestroyed || weatherPlace != key) return@runOnUiThread
                if (snapshot == null) {
                    binding.weatherText.isVisible = false
                    return@runOnUiThread
                }
                binding.weatherText.isVisible = true
                binding.weatherText.text = if (snapshot.rainPercent >= 20) {
                    getString(R.string.weather_line, snapshot.celsius, snapshot.rainPercent)
                } else {
                    getString(R.string.weather_dry, snapshot.celsius)
                }
            }
        }
    }

    private fun hijriToday(): String {
        val date = HijrahDate.from(LocalDate.now())
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy").withLocale(Locale.forLanguageTag("ar"))
        return date.format(formatter)
    }

    private fun rememberPlaceName(latitude: Double, longitude: Double) {
        val matched = PrayerCities.matching(latitude, longitude)
        if (matched != null) {
            applyPlaceName(getString(PrayerCopy.cityName(matched.id)))
            return
        }
        applyPlaceName(getString(R.string.place_my_location))
        if (!Geocoder.isPresent()) return
        val geocoder = Geocoder(this, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                val locality = addresses.firstOrNull()?.locality?.takeIf { it.isNotBlank() } ?: return@getFromLocation
                runOnUiThread { if (!isDestroyed) applyPlaceName(locality) }
            }
        } else {
            thread(name = "place-name") {
                val locality = runCatching {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()?.locality
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@thread
                runOnUiThread { if (!isDestroyed) applyPlaceName(locality) }
            }
        }
    }

    private fun applyPlaceName(name: String) {
        val current = placeStore.read()
        if (current.placeName != name) {
            placeStore.write(current.copy(placeName = name))
        }
        if (::binding.isInitialized && !isDestroyed) {
            binding.cityInput.setText(name, false)
        }
    }

    private fun wakeForAlarm() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val settings = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = "package:$packageName".toUri()
        }
        runCatching { startActivity(settings) }
    }

    private fun openDndSettings() {
        val settings = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        runCatching { startActivity(settings) }
            .onFailure { setStatus(getString(R.string.status_dnd_settings_failed), error = true) }
    }

    private fun requestBatteryExemption() {
        val power = getSystemService(PowerManager::class.java)
        if (power.isIgnoringBatteryOptimizations(packageName)) {
            openBatterySettings()
            return
        }
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:$packageName".toUri()
        }
        runCatching { startActivity(request) }
            .onFailure { openBatterySettings() }
    }

    private fun openBatterySettings() {
        val settings = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { startActivity(settings) }
            .onFailure { setStatus(getString(R.string.status_battery_settings_failed), error = true) }
    }

    private fun promptNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (notificationsGranted()) return
        val canAsk = !askedNotifications ||
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        if (canAsk) {
            askedNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openAppSettings()
        }
    }

    private fun maybePromptReadiness() {
        if (promptedThisProcess) return
        if (intent.getBooleanExtra(AlarmLaunch.EXTRA_RINGING, false)) return
        val ui = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
        if (ui.getBoolean(KEY_READINESS_SHOWN, false)) return
        val notificationsMissing = !notificationsGranted()
        val exactMissing = !app.alertScheduler.canScheduleExactAlarms()
        val batteryMissing = !batteryUnrestricted()
        if (!notificationsMissing && !exactMissing && !batteryMissing) return
        promptedThisProcess = true
        ui.edit().putBoolean(KEY_READINESS_SHOWN, true).commit()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.readiness_title)
            .setMessage(R.string.readiness_message)
            .setPositiveButton(R.string.readiness_fix) { _, _ ->
                when {
                    notificationsMissing -> promptNotifications()
                    exactMissing -> openExactAlarmSettings()
                    batteryMissing -> requestBatteryExemption()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun notificationsGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun batteryUnrestricted(): Boolean {
        return getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    }

    private fun updatePermissionSummary() {
        val notificationsGranted = notificationsGranted()
        val exactGranted = app.alertScheduler.canScheduleExactAlarms()
        val batteryGranted = batteryUnrestricted()
        val dndGranted = getSystemService(NotificationManager::class.java)
            .isNotificationPolicyAccessGranted
        binding.permissionText.text = getString(
            R.string.permission_summary,
            grantedLabel(notificationsGranted),
            grantedLabel(exactGranted),
            grantedLabel(batteryGranted),
            grantedLabel(dndGranted),
        )
        val showNotifications = !notificationsGranted
        val showExact = !exactGranted
        val showBattery = !batteryGranted
        binding.readinessCard.isVisible = showNotifications || showExact || showBattery
        binding.notificationsPromptButton.isVisible = showNotifications
        binding.exactPromptButton.isVisible = showExact
        binding.batteryPromptButton.isVisible = showBattery
    }

    private fun grantedLabel(granted: Boolean): String {
        return getString(if (granted) R.string.perm_granted else R.string.perm_missing)
    }

    private fun setStatus(message: String, error: Boolean = false) {
        if (message == getString(R.string.status_idle)) {
            binding.statusText.isVisible = false
            return
        }
        binding.statusText.isVisible = true
        binding.statusText.text = message
        binding.statusText.setTextColor(
            ContextCompat.getColor(this, if (error) R.color.error else R.color.on_surface),
        )
    }

    private companion object {
        const val PREVIEW_ALERT_ID = "phase1-sound-preview"
        const val TAG = "Adhan"
        const val UI_PREFS = "adhan_ui"
        const val KEY_READINESS_SHOWN = "readiness_shown"
        const val KEY_SECTION = "section"
        val offsetChoices = listOf(0, 5, 10, 15, 20, 30, 45, 60)
        val ringChoices = listOf(0, 10, 15, 20, 30, 45, 60, 90, 120)
        val fadeChoices = listOf(0, 4, 10)
        const val ALARM_ID_EXTRA = "com.adhan.app.extra.ALERT_ID"
        const val SECTION_EXTRA = "com.adhan.app.extra.SECTION"

        private var openedAlarmId: String? = null

        private var promptedThisProcess = false
        private var askedNotifications = false
    }
}
