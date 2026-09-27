package com.ghostmode.app.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ghostmode.app.R
import com.ghostmode.app.appGraph
import com.ghostmode.app.data.Preset
import com.ghostmode.app.data.SimSlotMode
import com.ghostmode.app.data.ThemeMode
import com.ghostmode.app.domain.DiagnosticsReport
import com.ghostmode.app.domain.FailureReason
import com.ghostmode.app.domain.TurnOutcome
import com.ghostmode.app.scheduling.ScheduleManager
import com.ghostmode.app.scheduling.TimerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiMessage(
    @param:StringRes val text: Int,
    val showLogAction: Boolean = false,
    val long: Boolean = showLogAction
)

enum class MobileDataStatus { ACTIVE, STANDBY_WIFI, UNAVAILABLE }

data class DiagnosticsUiState(
    val isRunning: Boolean = false,
    val report: DiagnosticsReport? = null,
    val mobileData: MobileDataStatus? = null,
    val failed: Boolean = false
)

data class SystemStatus(
    val isBatteryExempt: Boolean = true,
    val canScheduleExact: Boolean = true
)

private const val TILE_ALREADY_ADDED = 1 // StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
private const val TILE_ADDED = 2 // StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val graph = application.appGraph
    private val state = graph.state
    private val actions = graph.actions
    private val controller = graph.controller

    val isOn = state.isOn
    val isOnSinceMs = state.isOnTimestampMs
    val isBusy = controller.isBusy
    val backend = graph.shell.backend
    val shizukuStatus = graph.shizuku.status
    val presets = graph.presets.presets
    val activePresetId = state.activePresetId
    val appliedSnapshot = state.appliedSnapshot
    val simSlotMode = state.simSlotMode
    val timerFireAtMs = state.timerFireAtMs
    val sessions = state.sessions
    val scheduleEnabled = state.scheduleEnabled
    val scheduleStart = state.scheduleStartMinuteOfDay
    val scheduleEnd = state.scheduleEndMinuteOfDay
    val notificationEnabled = state.notificationEnabled
    val themeMode = state.themeMode
    val dynamicColor = state.dynamicColor
    val updateCheckEnabled = state.updateCheckEnabled
    val updateState = graph.updates.state
    val logEntries = state.logEntries

    val needsReapply: StateFlow<Boolean> = combine(state.isOn, state.appliedSnapshot) { _, _ ->
        controller.needsReapply()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, controller.needsReapply())

    private val diagnosticsFlow = MutableStateFlow(DiagnosticsUiState())
    val diagnostics: StateFlow<DiagnosticsUiState> = diagnosticsFlow.asStateFlow()

    private val systemStatusFlow = MutableStateFlow(SystemStatus())
    val systemStatus: StateFlow<SystemStatus> = systemStatusFlow.asStateFlow()

    private val pendingImportFlow = MutableStateFlow<List<Preset>?>(null)
    val pendingImport: StateFlow<List<Preset>?> = pendingImportFlow.asStateFlow()

    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    // --- Lifecycle --------------------------------------------------------------------------

    fun onResume() {
        graph.shizuku.refresh()
        // Not forced: a denied root request must not re-prompt (or toast) on every resume.
        viewModelScope.launch { graph.root.probeRoot() }
        refreshSystemStatus()
    }

    fun refreshSystemStatus() {
        val app = getApplication<Application>()
        val power = app.getSystemService(PowerManager::class.java)
        systemStatusFlow.value = SystemStatus(
            isBatteryExempt = power?.isIgnoringBatteryOptimizations(app.packageName) ?: true,
            canScheduleExact = ScheduleManager.canScheduleExact(app)
        )
    }

    /** Result of `StatusBarManager.requestAddTileService`; silence would look like a broken button. */
    fun onTileRequestResult(result: Int) {
        val text = when (result) {
            TILE_ADDED -> R.string.message_tile_added
            TILE_ALREADY_ADDED -> R.string.message_tile_already_added
            else -> R.string.message_tile_manual
        }
        viewModelScope.launch { messageChannel.send(UiMessage(text, long = text == R.string.message_tile_manual)) }
    }

    fun openShizuku() {
        graph.shizuku.openShizukuApp()
    }

    fun downloadShizuku() = graph.shizuku.openShizukuDownload()

    fun requestShizukuPermission() = graph.shizuku.requestPermission()

    // --- Mode -------------------------------------------------------------------------------

    fun toggle() = launchAction { if (isOn.value) actions.turnOff() else actions.turnOn() }

    fun turnOn(timerMinutes: Int? = null) = launchAction { actions.turnOn(timerMinutes) }

    fun turnOff() = launchAction { actions.turnOff() }

    fun reapply() = launchAction { actions.reapply() }

    /**
     * Shell sequences run in the process-wide scope: leaving the screen must never cut a
     * sequence in half. Only the resulting message is tied to the ViewModel.
     */
    private fun launchAction(block: suspend () -> TurnOutcome) {
        val wasOn = isOn.value
        val result = graph.scope.async { block() }
        viewModelScope.launch {
            messageFor(result.await(), wasOn)?.let { messageChannel.send(it) }
        }
    }

    private fun messageFor(outcome: TurnOutcome, wasOn: Boolean): UiMessage? = when (outcome) {
        is TurnOutcome.Success -> null
        is TurnOutcome.Partial -> UiMessage(
            if (wasOn) R.string.message_partial_off else R.string.message_partial_on,
            showLogAction = true
        )
        is TurnOutcome.Failure -> when (outcome.reason) {
            FailureReason.BUSY -> UiMessage(R.string.message_busy)
            FailureReason.NOT_READY -> UiMessage(R.string.message_not_ready)
            FailureReason.NO_PRESET -> UiMessage(R.string.message_no_preset)
            FailureReason.ALL_COMMANDS_FAILED -> UiMessage(R.string.message_all_failed, showLogAction = true)
        }
    }

    // --- Timer ------------------------------------------------------------------------------

    fun armTimerMinutes(minutes: Int) = actions.armTimerAt(System.currentTimeMillis() + minutes * 60_000L)

    fun armTimerUntilMorning() = actions.armTimerAt(TimerManager.morningBoundaryMs(System.currentTimeMillis()))

    fun cancelTimer() = actions.cancelTimer()

    // --- Settings ---------------------------------------------------------------------------

    fun selectPreset(presetId: String) {
        if (!controller.selectPreset(presetId)) {
            viewModelScope.launch { messageChannel.send(UiMessage(R.string.message_preset_locked)) }
        }
    }

    fun setSimSlotMode(mode: SimSlotMode) {
        if (isOn.value) {
            viewModelScope.launch { messageChannel.send(UiMessage(R.string.message_sim_locked)) }
            return
        }
        state.setSimSlotMode(mode)
    }

    fun setSchedule(enabled: Boolean, startMinute: Int, endMinute: Int) {
        state.setSchedule(enabled, startMinute, endMinute)
        ScheduleManager.update(getApplication())
    }

    fun setNotificationEnabled(enabled: Boolean) = state.setNotificationEnabled(enabled)

    fun setThemeMode(mode: ThemeMode) {
        state.setThemeMode(mode)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode.toAppCompatNightMode())
    }

    fun setDynamicColor(enabled: Boolean) = state.setDynamicColor(enabled)

    fun setUpdateCheckEnabled(enabled: Boolean) {
        state.setUpdateCheckEnabled(enabled)
        if (enabled) graph.updates.check()
    }

    fun checkForUpdates() = graph.updates.check()

    fun dismissUpdate() = graph.updates.dismiss()

    // --- Presets ----------------------------------------------------------------------------

    fun savePreset(preset: Preset) {
        graph.presets.saveCustomPreset(preset)
    }

    fun deletePreset(presetId: String) {
        if (presetId == activePresetId.value && isOn.value) {
            viewModelScope.launch { messageChannel.send(UiMessage(R.string.message_preset_locked)) }
            return
        }
        graph.presets.deleteCustomPreset(presetId)
        if (presetId == activePresetId.value) state.setActivePresetId(com.ghostmode.app.data.BuiltInPresets.DEFAULT_ID)
    }

    fun exportPresets(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(graph.presets.exportCustomPresetsJson().toByteArray())
                    } != null
                }.getOrDefault(false)
            }
            messageChannel.send(UiMessage(if (ok) R.string.message_export_done else R.string.message_export_failed))
        }
    }

    fun readImport(uri: Uri) {
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            val parsed = json?.let(graph.presets::parseImport)
            when {
                parsed == null -> messageChannel.send(UiMessage(R.string.message_import_invalid))
                parsed.isEmpty() -> messageChannel.send(UiMessage(R.string.message_import_empty))
                else -> pendingImportFlow.value = parsed
            }
        }
    }

    fun confirmImport() {
        val parsed = pendingImportFlow.value ?: return
        pendingImportFlow.value = null
        val added = graph.presets.importPresets(parsed)
        viewModelScope.launch {
            messageChannel.send(UiMessage(if (added > 0) R.string.message_import_done else R.string.message_import_duplicates))
        }
    }

    fun cancelImport() {
        pendingImportFlow.value = null
    }

    // --- Diagnostics & log ------------------------------------------------------------------

    fun runDiagnostics() {
        if (diagnosticsFlow.value.isRunning) return
        diagnosticsFlow.value = diagnosticsFlow.value.copy(isRunning = true, failed = false)
        viewModelScope.launch {
            val report = controller.runDiagnostics()
            diagnosticsFlow.value = DiagnosticsUiState(
                isRunning = false,
                report = report,
                mobileData = readMobileDataStatus(),
                failed = report == null
            )
        }
    }

    fun enablePackages(packages: List<String>) {
        val result = graph.scope.async { controller.enablePackages(packages) }
        viewModelScope.launch {
            val outcome = result.await()
            messageChannel.send(
                if (outcome is TurnOutcome.Failure) UiMessage(R.string.message_all_failed, showLogAction = true)
                else UiMessage(R.string.message_packages_enabled)
            )
            runDiagnostics()
        }
    }

    fun clearLog() = state.clearLog()

    fun logAsText(): String = logEntries.value.joinToString("\n\n") { entry ->
        buildString {
            append("$ ").append(entry.command).append("  [exit ").append(entry.exitCode).append(']')
            if (entry.stdout.isNotBlank()) append('\n').append(entry.stdout.trimEnd())
            if (entry.stderr.isNotBlank()) append('\n').append(entry.stderr.trimEnd())
        }
    }

    private fun readMobileDataStatus(): MobileDataStatus {
        val connectivity = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
            ?: return MobileDataStatus.UNAVAILABLE
        val active = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
        if (active?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
            active.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        ) {
            return MobileDataStatus.ACTIVE
        }
        @Suppress("DEPRECATION")
        val cellularAvailable = connectivity.allNetworks.any { network ->
            connectivity.getNetworkCapabilities(network)?.let {
                it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } == true
        }
        return if (cellularAvailable) MobileDataStatus.STANDBY_WIFI else MobileDataStatus.UNAVAILABLE
    }
}
