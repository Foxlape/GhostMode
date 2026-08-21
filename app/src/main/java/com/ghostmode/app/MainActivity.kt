package com.ghostmode.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ghostmode.app.data.CommandLogEntry
import com.ghostmode.app.data.GhostStateRepository
import com.ghostmode.app.data.Preset
import com.ghostmode.app.data.PresetRepository
import com.ghostmode.app.data.ThemeMode
import com.ghostmode.app.domain.GhostModeController
import com.ghostmode.app.scheduling.ScheduleManager
import com.ghostmode.app.scheduling.TimerManager
import com.ghostmode.app.shell.AutoShellExecutor
import com.ghostmode.app.shell.RootShellExecutor
import com.ghostmode.app.shell.ShellBackend
import com.ghostmode.app.shell.ShizukuManager
import com.ghostmode.app.service.StatusNotificationManager
import com.ghostmode.app.support.UpdateManager
import com.ghostmode.app.ui.AppScreen
import com.ghostmode.app.ui.theme.GhostModeTheme
import com.ghostmode.app.widget.GhostWidgetProvider
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        stateRepository.setNotificationEnabled(isGranted)
    }

    private var isBatteryExemptState by mutableStateOf(true)

    private lateinit var shizukuManager: ShizukuManager
    private lateinit var rootExecutor: RootShellExecutor
    private lateinit var autoExecutor: AutoShellExecutor
    private lateinit var presetRepository: PresetRepository
    private lateinit var stateRepository: GhostStateRepository
    private lateinit var ghostModeController: GhostModeController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shizukuManager = ShizukuManager(applicationContext)
        rootExecutor = RootShellExecutor()
        autoExecutor = AutoShellExecutor(rootExecutor, shizukuManager)
        presetRepository = PresetRepository.getInstance(applicationContext)
        stateRepository = GhostStateRepository.getInstance(this)
        ghostModeController = GhostModeController(autoExecutor, presetRepository, stateRepository)
        setContent {
            GhostModeTheme {
                GhostModeApp()
            }
        }
        handleShortcutIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        shizukuManager.start()
        lifecycleScope.launch { rootExecutor.probeRoot() }
        isBatteryExemptState = isIgnoringBatteryOptimizations()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }

    override fun onStop() {
        if (!ghostModeController.isBusy.value) {
            shizukuManager.stop()
        }
        super.onStop()
    }

    @Composable
    private fun GhostModeApp() {
        val shizukuStatus by shizukuManager.status.collectAsStateWithLifecycle()
        val shellBackend by autoExecutor.backend.collectAsStateWithLifecycle()
        val isGhostModeOn by stateRepository.isOn.collectAsStateWithLifecycle()
        val isBusy by ghostModeController.isBusy.collectAsStateWithLifecycle()
        val presets by presetRepository.presets.collectAsStateWithLifecycle()
        val activePresetId by stateRepository.activePresetId.collectAsStateWithLifecycle()
        val savedNetworkMask by stateRepository.savedNetworkMask.collectAsStateWithLifecycle()
        val logEntries by stateRepository.logEntries.collectAsStateWithLifecycle()
        val notificationEnabled by stateRepository.notificationEnabled.collectAsStateWithLifecycle()
        val simSlotMode by stateRepository.simSlotMode.collectAsStateWithLifecycle()
        val sessions by stateRepository.sessions.collectAsStateWithLifecycle()
        val scheduleEnabled by stateRepository.scheduleEnabled.collectAsStateWithLifecycle()
        val scheduleStartMinute by stateRepository.scheduleStartMinuteOfDay.collectAsStateWithLifecycle()
        val scheduleEndMinute by stateRepository.scheduleEndMinuteOfDay.collectAsStateWithLifecycle()
        val themeMode by stateRepository.themeMode.collectAsStateWithLifecycle()
        val timerFireAtMs by stateRepository.timerFireAtMs.collectAsStateWithLifecycle()
        val availableUpdate by UpdateManager.availableUpdate.collectAsStateWithLifecycle()

        val now = System.currentTimeMillis()
        val todayMidnight = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sevenDaysAgo = now - 7 * 24 * 3600 * 1000L

        val todayTotalMs = stateRepository.totalDurationMs(sessions, todayMidnight, now)
        val sevenDaysTotalMs = stateRepository.totalDurationMs(sessions, sevenDaysAgo, now)
        val allTimeTotalMs = stateRepository.totalDurationAllTimeMs(sessions, now)

        AppScreen(
            isOn = isGhostModeOn,
            onToggle = ::onToggle,
            isBusy = isBusy,
            shizukuStatus = shizukuStatus,
            isRootAvailable = shellBackend == ShellBackend.ROOT,
            onGrantPermission = shizukuManager::requestPermission,
            onOpenShizuku = shizukuManager::openShizukuApp,
            onDownloadShizuku = shizukuManager::openShizukuDownload,
            presets = presets,
            activePresetId = activePresetId,
            onSelectPreset = ghostModeController::selectPreset,
            onSavePreset = ::onSavePreset,
            onDeletePreset = presetRepository::deleteCustomPreset,
            onDuplicatePreset = ::onDuplicatePreset,
            onExportPresets = { uri -> writePresetsExport(uri) },
            onImportPresets = { uri -> readPresetsImport(uri) },
            savedNetworkMask = savedNetworkMask,
            onRunDiagnostics = { ghostModeController.runDiagnostics() },
            logEntries = logEntries,
            onClearLog = stateRepository::clearLog,
            onRemoveEntry = { stateRepository.removeLogEntry(it.timestampMs) },
            isScheduleEnabled = scheduleEnabled,
            scheduleStartMinutes = scheduleStartMinute,
            scheduleEndMinutes = scheduleEndMinute,
            onScheduleChanged = ::onScheduleChanged,
            notificationEnabled = notificationEnabled,
            onNotificationToggled = ::onNotificationToggled,
            simSlotMode = simSlotMode,
            onSimSlotModeChanged = stateRepository::setSimSlotMode,
            onRequestAddTile = ::requestAddQuickSettingsTile,
            sessionHistory = sessions,
            todayTotalMs = todayTotalMs,
            sevenDaysTotalMs = sevenDaysTotalMs,
            allTimeTotalMs = allTimeTotalMs,
            availableUpdate = availableUpdate,
            onCheckUpdates = { UpdateManager.refresh() },
            onDismissUpdate = { UpdateManager.dismiss() },
            onOpenUrl = ::openUrl,
            themeMode = themeMode,
            onThemeChanged = ::onThemeChanged,
            isBatteryExempt = isBatteryExemptState,
            onRequestIgnoreBatteryOptimization = ::requestIgnoreBatteryOptimization,
            timerFireAtMs = timerFireAtMs,
            onArmTimerMinutes = ::armTimerMinutes,
            onArmTimerUntilMorning = ::armTimerMorning,
            onCancelTimer = ::cancelTimer
        )
    }

    private fun requestAddQuickSettingsTile() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = getSystemService(android.app.StatusBarManager::class.java) ?: return
            statusBarManager.requestAddTileService(
                android.content.ComponentName(this, com.ghostmode.app.tile.GhostTileService::class.java),
                getString(R.string.tile_label),
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_ghost),
                mainExecutor
            ) { }
        }
    }

    private fun onToggle() {
        val shouldTurnOn = !stateRepository.isOn.value
        lifecycleScope.launch {
            if (shouldTurnOn) ghostModeController.turnOn() else ghostModeController.turnOff()
            syncSystemUi()
        }
    }

    private suspend fun syncSystemUi() {
        StatusNotificationManager.update(
            this@MainActivity,
            stateRepository.isOn.value,
            stateRepository.notificationEnabled.value,
            stateRepository.isOnTimestampMs.value
        )
        GhostWidgetProvider.refreshAll(this@MainActivity, stateRepository.isOn.value)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SHORTCUT_TURN_ON -> lifecycleScope.launch { performShortcutTurn(turnOn = true) }
            ACTION_SHORTCUT_TURN_OFF -> lifecycleScope.launch { performShortcutTurn(turnOn = false) }
            ACTION_SHORTCUT_TIMER_1H -> lifecycleScope.launch {
                if (!stateRepository.isOn.value) ghostModeController.turnOn()
                if (stateRepository.isOn.value) armTimerMinutes(TIMER_ONE_HOUR_MINUTES)
                syncSystemUi()
            }
        }
    }

    private suspend fun performShortcutTurn(turnOn: Boolean) {
        if (turnOn && !stateRepository.isOn.value) ghostModeController.turnOn()
        if (!turnOn && stateRepository.isOn.value) ghostModeController.turnOff()
        syncSystemUi()
    }

    private fun onThemeChanged(mode: ThemeMode) {
        stateRepository.setThemeMode(mode)
        AppCompatDelegate.setDefaultNightMode(mode.toAppCompatNightMode())
    }

    private fun requestIgnoreBatteryOptimization() {
        if (isIgnoringBatteryOptimizations()) return
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (error: Exception) {
                Log.e(TAG, "Battery optimization screen unavailable", error)
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean =
        getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) ?: true

    private fun armTimerMinutes(minutes: Int) {
        armTimerAt(System.currentTimeMillis() + minutes * MILLIS_PER_MINUTE)
    }

    private fun armTimerMorning() {
        armTimerAt(TimerManager.morningBoundaryMs(System.currentTimeMillis()))
    }

    private fun armTimerAt(fireAtMs: Long) {
        stateRepository.setTimerFireAtMs(fireAtMs)
        TimerManager.schedule(this, fireAtMs)
    }

    private fun cancelTimer() {
        TimerManager.cancel(this)
        stateRepository.clearTimerFireAt()
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun onDuplicatePreset(preset: Preset) {
        val duplicated = preset.copy(
            id = java.util.UUID.randomUUID().toString(),
            title = "${preset.title} (копия)",
            isBuiltIn = false
        )
        presetRepository.saveCustomPreset(duplicated)
    }

    private fun onSavePreset(preset: Preset) {
        try {
            presetRepository.saveCustomPreset(preset)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun onScheduleChanged(enabled: Boolean, startMin: Int, endMin: Int) {
        stateRepository.setScheduleEnabled(enabled)
        stateRepository.setScheduleStartMinuteOfDay(startMin)
        stateRepository.setScheduleEndMinuteOfDay(endMin)
        ScheduleManager.update(this)
    }

    private fun onNotificationToggled(enabled: Boolean) {
        if (enabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        stateRepository.setNotificationEnabled(enabled)
    }

    private fun writePresetsExport(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(presetRepository.exportCustomPresetsJson().toByteArray())
            }
        } catch (error: Exception) {
            Log.e(TAG, "Presets export failed", error)
        }
    }

    private fun readPresetsImport(uri: Uri) {
        val importedJson = try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes().toString(Charsets.UTF_8)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Presets import read failed", error)
            null
        } ?: return
        val importedCount = presetRepository.importPresetsJson(importedJson)
        stateRepository.appendLog(
            CommandLogEntry(
                timestampMs = System.currentTimeMillis(),
                command = IMPORT_LOG_COMMAND,
                stdout = IMPORT_LOG_RESULT_PREFIX + importedCount,
                stderr = IMPORT_LOG_EMPTY_OUTPUT,
                exitCode = if (importedCount >= 0) IMPORT_LOG_SUCCESS else IMPORT_LOG_FAILURE
            )
        )
    }

    companion object {
        private const val TAG = "GhostMain"

        private const val ACTION_SHORTCUT_TURN_ON = "com.ghostmode.app.shortcut.TURN_ON"
        private const val ACTION_SHORTCUT_TURN_OFF = "com.ghostmode.app.shortcut.TURN_OFF"
        private const val ACTION_SHORTCUT_TIMER_1H = "com.ghostmode.app.shortcut.TIMER_1H"
        private const val TIMER_ONE_HOUR_MINUTES = 60
        private const val MILLIS_PER_MINUTE = 60_000L

        private const val IMPORT_LOG_COMMAND = "Импорт пресетов"
        private const val IMPORT_LOG_RESULT_PREFIX = "Добавлено: "
        private const val IMPORT_LOG_EMPTY_OUTPUT = ""
        private const val IMPORT_LOG_SUCCESS = 0
        private const val IMPORT_LOG_FAILURE = -1
    }
}
