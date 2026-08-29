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
        if (!canRun() || !isBusyFlow.compareAndSet(false, true)) return TurnOutcome.Failure(emptyList())
        try {
            val preset = resolveActivePreset() ?: return TurnOutcome.Failure(emptyList())
            val slots = getActiveSlots()
            captureNetworkMasksIfMissing(preset, slots)
            val commands = expandCommandsForSlots(preset.onCommands, slots)
            val results = commands.map { command -> executeAndLog(command) }
            val isSuccess = results.any { it.isSuccess }
            stateRepository.setIsOn(isSuccess)
            return outcomeFor(results)
        } finally {
            isBusyFlow.value = false
        }
    }

    suspend fun turnOff(): TurnOutcome {
        if (!canRun() || !isBusyFlow.compareAndSet(false, true)) return TurnOutcome.Failure(emptyList())
        try {
            val preset = resolveActivePreset() ?: return TurnOutcome.Failure(emptyList())
            val slots = getActiveSlots()
            val results = mutableListOf<CommandResult>()
            for (cmd in preset.offCommands) {
                if (cmd.contains(SLOT_MARKER)) {
                    for (slot in slots) {
                        val slotCmd = cmd.replaceFirst(SLOT_MARKER, "-s $slot")
                        val res = executeOffCommandForSlot(slotCmd, slot)
                        if (res != null) results.add(res)
                    }
                } else {
                    val res = executeOffCommandForSlot(cmd, 0)
                    if (res != null) results.add(res)
                }
            }
            val isRestored = results.any { it.isSuccess }
            stateRepository.setIsOn(!isRestored)
            return outcomeFor(results)
        } finally {
            isBusyFlow.value = false
        }
    }

    suspend fun runDiagnostics(): List<CommandResult> {
        if (!canRun() || !isBusyFlow.compareAndSet(false, true)) return emptyList()
        try {
            val slots = getActiveSlots()
            val baseCommands = listOf(
                BuiltInPresets.MASK_CAPTURE_COMMAND,
                BuiltInPresets.GET_IMS_SERVICE_DEVICE_COMMAND,
                BuiltInPresets.GET_IMS_SERVICE_CARRIER_COMMAND
            )
            val commands = expandCommandsForSlots(baseCommands, slots) + DIAGNOSTICS_IMS_COMMAND + DIAGNOSTICS_IMS_PACKAGES_COMMAND + DIAGNOSTICS_SETTINGS_SECURE_COMMAND + DIAGNOSTICS_SETTINGS_GLOBAL_COMMAND + DIAGNOSTICS_SETTINGS_SYSTEM_COMMAND
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

    private fun getActiveSlots(): List<Int> =
        stateRepository.simSlotMode.value.slots

    private fun expandCommandsForSlots(commands: List<String>, slots: List<Int>): List<String> {
        val expanded = mutableListOf<String>()
        for (cmd in commands) {
            if (cmd.contains(SLOT_MARKER)) {
                for (slot in slots) {
                    expanded.add(cmd.replaceFirst(SLOT_MARKER, "-s $slot"))
                }
            } else {
                expanded.add(cmd)
            }
        }
        return expanded
    }

    private suspend fun captureNetworkMasksIfMissing(preset: Preset, slots: List<Int>) {
        val baseCaptureCommand = preset.networkMaskCaptureCommand ?: return
        for (slot in slots) {
            if (stateRepository.getSavedNetworkMaskForSlot(slot) != null) continue
            val captureCmd = if (baseCaptureCommand.contains(SLOT_MARKER)) {
                baseCaptureCommand.replaceFirst(SLOT_MARKER, "-s $slot")
            } else {
                baseCaptureCommand
            }
            val executedResult = executeSafely(captureCmd)
            if (!executedResult.isSuccess) {
                stateRepository.appendLog(executedResult.toLogEntry())
                continue
            }
            val networkMask = MASK_VALUE_PATTERN
                .findAll(executedResult.stdout)
                .lastOrNull()
                ?.value
            if (networkMask != null) {
                stateRepository.appendLog(executedResult.toLogEntry())
                stateRepository.setSavedNetworkMaskForSlot(slot, networkMask)
            } else {
                applyFallbackRestoreMaskForSlot(captureCmd, slot)
            }
        }
    }

    private fun applyFallbackRestoreMaskForSlot(command: String, slot: Int) {
        stateRepository.appendLog(fallbackMaskLogEntry(command))
        stateRepository.setSavedNetworkMaskForSlot(slot, FALLBACK_RESTORE_MASK)
    }

    private fun fallbackMaskLogEntry(command: String): CommandLogEntry =
        CommandLogEntry(
            timestampMs = System.currentTimeMillis(),
            command = command,
            stdout = CAPTURE_FALLBACK_MASK_NOTE,
            stderr = EMPTY_OUTPUT,
            exitCode = EXIT_SKIPPED
        )

    private suspend fun executeOffCommandForSlot(command: String, slot: Int): CommandResult? {
        val savedMask = stateRepository.getSavedNetworkMaskForSlot(slot)
            ?: stateRepository.savedNetworkMask.value
        if (savedMask == null && command.contains(BuiltInPresets.MASK_PLACEHOLDER)) {
            stateRepository.appendLog(skippedLogEntry(command))
            return null
        }
        val resolvedCommand = command.replace(BuiltInPresets.MASK_PLACEHOLDER, savedMask ?: FALLBACK_RESTORE_MASK)
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
        const val DIAGNOSTICS_IMS_PACKAGES_COMMAND = "pm list packages | grep -i ims"
        const val DIAGNOSTICS_SETTINGS_SECURE_COMMAND = "settings list secure | grep -i -E \"volte|ims\""
        const val DIAGNOSTICS_SETTINGS_GLOBAL_COMMAND = "settings list global | grep -i -E \"volte|ims\""
        const val DIAGNOSTICS_SETTINGS_SYSTEM_COMMAND = "settings list system | grep -i -E \"volte|ims\""
        const val FALLBACK_RESTORE_MASK = "11001111101111111111"
        const val SLOT_MARKER = "-s 0"

        private val MASK_VALUE_PATTERN = Regex("(?<![0-9])[01]{16,32}(?![0-9])")

        private const val EXIT_COMMAND_FAILURE = -1
        private const val EMPTY_OUTPUT = ""
        private const val CAPTURE_FALLBACK_MASK_NOTE =
            "Прошивка вернула список сетей вместо битовой маски. " +
                "Использована маска по умолчанию (все стандартные сети) — " +
                "восстановление вернёт полный набор сетей."
    }
}
