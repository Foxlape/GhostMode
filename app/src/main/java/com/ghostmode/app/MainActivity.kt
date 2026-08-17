package com.ghostmode.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ghostmode.app.data.CommandLogEntry
import com.ghostmode.app.data.GhostStateRepository
import com.ghostmode.app.data.Preset
import com.ghostmode.app.data.PresetRepository
import com.ghostmode.app.domain.GhostModeController
import com.ghostmode.app.scheduling.ScheduleManager
import com.ghostmode.app.shell.AutoShellExecutor
import com.ghostmode.app.shell.RootShellExecutor
import com.ghostmode.app.shell.ShellBackend
import com.ghostmode.app.shell.ShizukuManager
import com.ghostmode.app.ui.AppScreen
import com.ghostmode.app.ui.theme.GhostModeTheme
import com.ghostmode.app.widget.GhostWidgetProvider
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val presetExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(PRESETS_EXPORT_MIME_TYPE)
    ) { uri -> if (uri != null) writePresetsExport(uri) }

    private val presetImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) readPresetsImport(uri) }

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
        presetRepository = PresetRepository(this)
        stateRepository = GhostStateRepository(this)
        ghostModeController = GhostModeController(autoExecutor, presetRepository, stateRepository)
        setContent {
            GhostModeTheme {
                GhostModeApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        shizukuManager.start()
        lifecycleScope.launch { rootExecutor.probeRoot() }
    }

    override fun onStop() {
        shizukuManager.stop()
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
        val sessions by stateRepository.sessions.collectAsStateWithLifecycle()
        val scheduleEnabled by stateRepository.scheduleEnabled.collectAsStateWithLifecycle()
        val scheduleStartMinute by stateRepository.scheduleStartMinuteOfDay.collectAsStateWithLifecycle()
        val scheduleEndMinute by stateRepository.scheduleEndMinuteOfDay.collectAsStateWithLifecycle()

        AppScreen(
            shizukuStatus = shizukuStatus,
            rootAvailable = shellBackend == ShellBackend.ROOT,
            isGhostModeOn = isGhostModeOn,
            isBusy = isBusy,
            presets = presets,
            activePresetId = activePresetId,
            savedNetworkMask = savedNetworkMask,
            logEntries = logEntries,
            onRequestPermission = shizukuManager::requestPermission,
            onOpenShizuku = shizukuManager::openShizukuApp,
            onDownloadShizuku = shizukuManager::openShizukuDownload,
            onToggle = ::onToggle,
            onSelectPreset = ghostModeController::selectPreset,
            onSavePreset = ::onSavePreset,
            onDeletePreset = presetRepository::deleteCustomPreset,
            onRunDiagnostics = ::onRunDiagnostics,
            onClearLog = stateRepository::clearLog,
            onRemoveEntry = { stateRepository.removeLogEntry(it.timestampMs) },
            notificationEnabled = notificationEnabled,
            onNotificationEnabled = stateRepository::setNotificationEnabled,
            onLanguageSelected = ::onLanguageSelected,
            sessions = sessions,
            scheduleEnabled = scheduleEnabled,
            scheduleStartMinute = scheduleStartMinute,
            scheduleEndMinute = scheduleEndMinute,
            onScheduleEnabled = ::onScheduleEnabled,
            onScheduleStart = ::onScheduleStartMinuteOfDay,
            onScheduleEnd = ::onScheduleEndMinuteOfDay,
            onExportPresets = { presetExportLauncher.launch(PRESETS_EXPORT_SUGGESTED_NAME) },
            onImportPresets = { presetImportLauncher.launch(PRESETS_IMPORT_MIME_TYPES) }
        )
    }

    private fun onToggle(enabled: Boolean) {
        lifecycleScope.launch {
            if (enabled) ghostModeController.turnOn() else ghostModeController.turnOff()
            GhostWidgetProvider.refreshAll(this@MainActivity, stateRepository.isOn.value)
        }
    }

    private fun onRunDiagnostics() {
        lifecycleScope.launch {
            ghostModeController.runDiagnostics()
        }
    }

    private fun onSavePreset(preset: Preset) {
        try {
            presetRepository.saveCustomPreset(preset)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun onLanguageSelected(languageTag: String) {
        val applicationLocales = when (languageTag) {
            LANGUAGE_TAG_RU -> LocaleListCompat.forLanguageTags(LANGUAGE_TAG_RU)
            LANGUAGE_TAG_EN -> LocaleListCompat.forLanguageTags(LANGUAGE_TAG_EN)
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(applicationLocales)
    }

    private fun onScheduleEnabled(enabled: Boolean) {
        stateRepository.setScheduleEnabled(enabled)
        ScheduleManager.update(this)
    }

    private fun onScheduleStartMinuteOfDay(minuteOfDay: Int) {
        stateRepository.setScheduleStartMinuteOfDay(minuteOfDay)
        ScheduleManager.update(this)
    }

    private fun onScheduleEndMinuteOfDay(minuteOfDay: Int) {
        stateRepository.setScheduleEndMinuteOfDay(minuteOfDay)
        ScheduleManager.update(this)
    }

    private fun writePresetsExport(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(presetRepository.exportCustomPresetsJson().toByteArray())
            }
        } catch (_: Exception) {
        }
    }

    private fun readPresetsImport(uri: Uri) {
        val importedJson = try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes().toString(Charsets.UTF_8)
            }
        } catch (_: Exception) {
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
        private const val LANGUAGE_TAG_RU = "ru"
        private const val LANGUAGE_TAG_EN = "en"
        private const val PRESETS_EXPORT_MIME_TYPE = "application/json"
        private const val PRESETS_EXPORT_SUGGESTED_NAME = "ghost-presets.json"
        private const val IMPORT_LOG_COMMAND = "Импорт пресетов"
        private const val IMPORT_LOG_RESULT_PREFIX = "Добавлено: "
        private const val IMPORT_LOG_EMPTY_OUTPUT = ""
        private const val IMPORT_LOG_SUCCESS = 0
        private const val IMPORT_LOG_FAILURE = -1
        private val PRESETS_IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain")
    }
}
