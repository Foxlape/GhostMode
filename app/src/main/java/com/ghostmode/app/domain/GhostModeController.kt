package com.ghostmode.app.domain

import com.ghostmode.app.data.BuiltInPresets
import com.ghostmode.app.data.CommandLogEntry
import com.ghostmode.app.data.GhostStateRepository
import com.ghostmode.app.data.Preset
import com.ghostmode.app.data.PresetRepository
import com.ghostmode.app.shell.CommandResult
import com.ghostmode.app.shell.ShellExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface TurnOutcome {
    data class Success(val results: List<CommandResult>) : TurnOutcome
    data class Failure(val results: List<CommandResult>) : TurnOutcome
}

class GhostModeController(
    private val shellExecutor: ShellExecutor,
    private val presetRepository: PresetRepository,
    private val stateRepository: GhostStateRepository
) {

    private val isBusyFlow = MutableStateFlow(false)

    val isBusy: StateFlow<Boolean> = isBusyFlow.asStateFlow()

    suspend fun turnOn(): TurnOutcome {
        if (!canRun()) return TurnOutcome.Failure(emptyList())
        isBusyFlow.value = true
        try {
            val preset = resolveActivePreset() ?: return TurnOutcome.Failure(emptyList())
            val captureFailure = captureNetworkMaskIfMissing(preset)
            if (captureFailure != null) return TurnOutcome.Failure(listOf(captureFailure))
            val results = preset.onCommands.map { command -> executeAndLog(command) }
            stateRepository.setIsOn(results.all { it.isSuccess })
            return outcomeFor(results)
        } finally {
            isBusyFlow.value = false
        }
    }

    suspend fun turnOff(): TurnOutcome {
        if (!canRun()) return TurnOutcome.Failure(emptyList())
        isBusyFlow.value = true
        try {
            val preset = resolveActivePreset() ?: return TurnOutcome.Failure(emptyList())
            val results = preset.offCommands.mapNotNull { command -> executeOffCommand(command) }
            stateRepository.setIsOn(false)
            return outcomeFor(results)
        } finally {
            isBusyFlow.value = false
        }
    }

    suspend fun runDiagnostics(): List<CommandResult> {
        if (!canRun()) return emptyList()
        isBusyFlow.value = true
        try {
            val commands = listOf(
                BuiltInPresets.MASK_CAPTURE_COMMAND,
                BuiltInPresets.GET_IMS_SERVICE_DEVICE_COMMAND,
                BuiltInPresets.GET_IMS_SERVICE_CARRIER_COMMAND,
                DIAGNOSTICS_IMS_COMMAND
            )
            return commands.map { command -> executeAndLog(command) }
        } finally {
            isBusyFlow.value = false
        }
    }

    fun selectPreset(presetId: String) {
        if (presetRepository.getPreset(presetId) == null) return
        stateRepository.setActivePresetId(presetId)
    }

    private suspend fun canRun(): Boolean =
        !isBusyFlow.value && (shellExecutor.readiness.value || (shellExecutor as? com.ghostmode.app.shell.AutoShellExecutor)?.isReady() == true)

    private fun resolveActivePreset(): Preset? =
        presetRepository.getPreset(stateRepository.activePresetId.value)
            ?: presetRepository.getPreset(BuiltInPresets.DEFAULT_ID)

    private suspend fun captureNetworkMaskIfMissing(preset: Preset): CommandResult? {
        val captureCommand = preset.networkMaskCaptureCommand ?: return null
        if (stateRepository.savedNetworkMask.value != null) return null
        val executedResult = executeSafely(captureCommand)
        if (!executedResult.isSuccess) {
            stateRepository.appendLog(executedResult.toLogEntry())
            return executedResult
        }
        val networkMask = executedResult.stdout.lines()
            .lastOrNull { line -> MASK_VALUE_PATTERN.matches(line.trim()) }
            ?.trim()
        if (networkMask == null) {
            applyFallbackRestoreMask(captureCommand)
            return null
        }
        stateRepository.appendLog(executedResult.toLogEntry())
        stateRepository.setSavedNetworkMask(networkMask)
        return null
    }

    private fun applyFallbackRestoreMask(command: String) {
        stateRepository.appendLog(fallbackMaskLogEntry(command))
        stateRepository.setSavedNetworkMask(FALLBACK_RESTORE_MASK)
    }

    private fun fallbackMaskLogEntry(command: String): CommandLogEntry =
        CommandLogEntry(
            timestampMs = System.currentTimeMillis(),
            command = command,
            stdout = CAPTURE_FALLBACK_MASK_NOTE,
            stderr = EMPTY_OUTPUT,
            exitCode = EXIT_SKIPPED
        )

    private suspend fun executeOffCommand(command: String): CommandResult? {
        val savedMask = stateRepository.savedNetworkMask.value
        if (savedMask == null && command.contains(BuiltInPresets.MASK_PLACEHOLDER)) {
            stateRepository.appendLog(skippedLogEntry(command))
            return null
        }
        val resolvedCommand = command.replace(BuiltInPresets.MASK_PLACEHOLDER, savedMask.orEmpty())
        return executeAndLog(resolvedCommand)
    }

    private suspend fun executeAndLog(command: String): CommandResult {
        val result = executeSafely(command)
        stateRepository.appendLog(result.toLogEntry())
        return result
    }

    private suspend fun executeSafely(command: String): CommandResult =
        try {
            shellExecutor.execute(command)
        } catch (error: Exception) {
            failedResult(command, error.message ?: error.javaClass.simpleName)
        }

    private fun failedResult(command: String, reason: String): CommandResult =
        CommandResult(
            command = command,
            stdout = EMPTY_OUTPUT,
            stderr = reason,
            exitCode = EXIT_COMMAND_FAILURE
        )

    private fun skippedLogEntry(command: String): CommandLogEntry =
        CommandLogEntry(
            timestampMs = System.currentTimeMillis(),
            command = command,
            stdout = SKIP_REASON_NO_MASK,
            stderr = EMPTY_OUTPUT,
            exitCode = EXIT_SKIPPED
        )

    private fun CommandResult.toLogEntry(): CommandLogEntry =
        CommandLogEntry(
            timestampMs = System.currentTimeMillis(),
            command = command,
            stdout = stdout,
            stderr = stderr,
            exitCode = exitCode
        )

    private fun outcomeFor(results: List<CommandResult>): TurnOutcome =
        if (results.all { it.isSuccess }) TurnOutcome.Success(results) else TurnOutcome.Failure(results)

    companion object {
        const val EXIT_SKIPPED = -1
        const val SKIP_REASON_NO_MASK = "Пропущено: сохранённая маска сети не найдена"
        const val DIAGNOSTICS_IMS_COMMAND = "dumpsys ims"
        const val FALLBACK_RESTORE_MASK = "11001111101111111111"

        private val MASK_VALUE_PATTERN = Regex("[01]{16,24}")

        private const val EXIT_COMMAND_FAILURE = -1
        private const val EMPTY_OUTPUT = ""
        private const val CAPTURE_FALLBACK_MASK_NOTE =
            "Прошивка вернула список сетей вместо битовой маски. " +
                "Использована маска по умолчанию (все стандартные сети) — " +
                "восстановление вернёт полный набор сетей."
    }
}
