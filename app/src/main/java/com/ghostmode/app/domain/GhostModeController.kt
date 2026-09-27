package com.ghostmode.app.domain

import com.ghostmode.app.data.AppliedSnapshot
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
import kotlinx.coroutines.sync.Mutex

sealed interface TurnOutcome {
    val results: List<CommandResult>

    /** Every command succeeded. */
    data class Success(override val results: List<CommandResult>) : TurnOutcome

    /** The mode switched, but some commands failed (see the command log). */
    data class Partial(override val results: List<CommandResult>) : TurnOutcome

    /** Nothing changed. */
    data class Failure(
        val reason: FailureReason,
        override val results: List<CommandResult> = emptyList()
    ) : TurnOutcome
}

enum class FailureReason { BUSY, NOT_READY, NO_PRESET, ALL_COMMANDS_FAILED }

data class SlotDiagnostics(
    val slot: Int,
    val networkMask: String?,
    val boundImsPackages: List<String>
) {
    val blocksLegacyVoice: Boolean? get() = networkMask?.let(NetworkMask::blocksLegacyVoice)
}

data class DiagnosticsReport(
    val slots: List<SlotDiagnostics>,
    val disabledImsPackages: List<String>
) {
    /** Heuristic: no 2G/3G fallback and no IMS service bound on every checked slot. */
    val callsLikelyBlocked: Boolean
        get() = slots.isNotEmpty() && slots.all { it.blocksLegacyVoice == true && it.boundImsPackages.isEmpty() }
}

/**
 * Applies and reverts presets.
 *
 * Turning the mode on records an [AppliedSnapshot] (original network masks, original values
 * of touched `settings` keys, packages that were disabled) so that turning it off restores
 * exactly what was changed — even if the user switched preset or SIM mode in between, or the
 * device rebooted.
 */
class GhostModeController(
    private val shell: ShellExecutor,
    private val presets: PresetRepository,
    private val state: GhostStateRepository,
    private val bootCount: () -> Int = { AppliedSnapshot.BOOT_COUNT_UNKNOWN },
    private val clock: () -> Long = System::currentTimeMillis,
    private val readyTimeoutMs: Long = DEFAULT_READY_TIMEOUT_MS
) {

    private val mutex = Mutex()
    private val busyFlow = MutableStateFlow(false)

    val isBusy: StateFlow<Boolean> = busyFlow.asStateFlow()

    suspend fun turnOn(): TurnOutcome = exclusive { applyOn() }

    /** Re-runs the ON commands of the applied snapshot, e.g. after a reboot. */
    suspend fun reapply(): TurnOutcome = exclusive { applyOn() }

    suspend fun turnOff(): TurnOutcome = exclusive { applyOff() }

    suspend fun toggle(): TurnOutcome = if (state.isOn.value) turnOff() else turnOn()

    /** `true` when the mode is on but a reboot has reverted the non-persistent commands. */
    fun needsReapply(): Boolean {
        if (!state.isOn.value) return false
        val applied = state.appliedSnapshot.value?.bootCount ?: return false
        val current = bootCount()
        return applied != AppliedSnapshot.BOOT_COUNT_UNKNOWN &&
            current != AppliedSnapshot.BOOT_COUNT_UNKNOWN &&
            applied != current
    }

    /** Changing the preset while the mode is on would make the next OFF inconsistent. */
    fun selectPreset(presetId: String): Boolean {
        if (state.isOn.value || presets.getPreset(presetId) == null) return false
        state.setActivePresetId(presetId)
        return true
    }

    suspend fun runDiagnostics(): DiagnosticsReport? {
        if (!mutex.tryLock()) return null
        busyFlow.value = true
        try {
            if (!shell.awaitReady(readyTimeoutMs)) return null
            val slots = state.appliedSnapshot.value?.slots ?: state.simSlotMode.value.slots
            val slotReports = slots.map { slot ->
                val maskResult = execute(forSlot(BuiltInPresets.MASK_CAPTURE_COMMAND, slot))
                SlotDiagnostics(
                    slot = slot,
                    networkMask = if (maskResult.isSuccess) NetworkMask.parse(maskResult.stdout) else null,
                    boundImsPackages = discoverImsPackages(listOf(slot))
                )
            }
            val disabled = listDisabledPackages().filter { it.contains(IMS_KEYWORD, ignoreCase = true) }
            DIAGNOSTICS_EXTRA_COMMANDS.forEach { execute(it) }
            return DiagnosticsReport(slotReports, disabled.sorted())
        } finally {
            busyFlow.value = false
            mutex.unlock()
        }
    }

    /** Repair helper: re-enables packages left disabled (for example by older app versions). */
    suspend fun enablePackages(packages: List<String>): TurnOutcome = exclusive {
        val results = packages.map { execute("pm enable $it") }
        outcomeOf(results, changed = results.any { it.isSuccess })
    }

    // --- ON ---------------------------------------------------------------------------------

    private suspend fun applyOn(): TurnOutcome {
        val wasOn = state.isOn.value
        // Legacy state (ON without a snapshot, saved by 0.1.x) is migrated on the first re-apply.
        val previous = state.appliedSnapshot.value ?: if (wasOn) legacySnapshot() else null
        val preset = (if (wasOn && previous != null) presets.getPreset(previous.presetId) else null)
            ?: resolveActivePreset()
            ?: return TurnOutcome.Failure(FailureReason.NO_PRESET)
        val slots = if (wasOn && previous != null) previous.slots else state.simSlotMode.value.slots

        captureNetworkMasks(preset, slots, freshCapture = !wasOn)

        val commands = resolveImsPackages(expandSlots(preset.onCommands, slots), slots)
        val settingsOriginals = previous?.settingsOriginals.orEmpty().toMutableMap()
        if (!wasOn) captureSettings(commands, settingsOriginals)

        val alreadyDisabled = if (commands.any { PM_DISABLE.matches(it) }) listDisabledPackages() else emptySet()
        val disabledByUs = previous?.disabledPackages.orEmpty().toMutableSet()
        val results = commands.map { command ->
            execute(command).also { result ->
                val pkg = PM_DISABLE.matchEntire(command)?.groupValues?.get(1)
                if (pkg != null && pkg !in alreadyDisabled && result.isSuccess &&
                    result.stdout.contains(DISABLED_STATE_MARKER)
                ) {
                    disabledByUs += pkg
                }
            }
        }

        val changed = results.any { it.isSuccess }
        if (changed) {
            state.markOn(
                AppliedSnapshot(
                    presetId = preset.id,
                    offCommands = preset.offCommands,
                    slots = slots,
                    settingsOriginals = settingsOriginals,
                    disabledPackages = disabledByUs.sorted(),
                    bootCount = bootCount(),
                    appliedAtMs = clock()
                )
            )
        }
        return outcomeOf(results, changed)
    }

    /**
     * Saves the network mask to restore later. A fresh capture happens on every OFF→ON
     * transition; an LTE-only reading is never trusted as "original" if a better one is known
     * (it means a previous session was not restored).
     */
    private suspend fun captureNetworkMasks(preset: Preset, slots: List<Int>, freshCapture: Boolean) {
        val captureCommand = preset.networkMaskCaptureCommand ?: return
        for (slot in slots) {
            val saved = state.getSavedNetworkMask(slot)
            if (!freshCapture && saved != null) continue
            val result = execute(forSlot(captureCommand, slot))
            val captured = if (result.isSuccess) NetworkMask.parse(result.stdout) else null
            val mask = when {
                captured == null -> saved ?: NetworkMask.FALLBACK_ALL.also { logNote(captureCommand, NOTE_MASK_FALLBACK) }
                captured == NetworkMask.LTE_ONLY -> saved ?: NetworkMask.FALLBACK_ALL.also { logNote(captureCommand, NOTE_MASK_FALLBACK) }
                else -> captured
            }
            state.setSavedNetworkMask(slot, mask)
        }
    }

    private suspend fun captureSettings(commands: List<String>, originals: MutableMap<String, String?>) {
        for (command in commands) {
            val match = SETTINGS_PUT.matchEntire(command) ?: continue
            val (namespace, key) = match.destructured
            val id = "$namespace/$key"
            if (id in originals) continue
            val result = execute("settings get $namespace $key")
            if (!result.isSuccess) continue
            val value = result.stdout.trim().lineSequence().firstOrNull()?.trim().orEmpty()
            // Empty output means "unknown": keep the preset's own restore value in that case.
            if (value.isEmpty()) continue
            originals[id] = value.takeUnless { it == SETTINGS_NULL }
        }
    }

    private suspend fun resolveImsPackages(commands: List<String>, slots: List<Int>): List<String> {
        if (commands.none { it.contains(BuiltInPresets.IMS_PACKAGES_PLACEHOLDER) }) return commands
        val packages = discoverImsPackages(slots)
        return commands.flatMap { command ->
            if (!command.contains(BuiltInPresets.IMS_PACKAGES_PLACEHOLDER)) {
                listOf(command)
            } else {
                if (packages.isEmpty()) logNote(command, NOTE_NO_IMS_PACKAGES)
                packages.map { command.replace(BuiltInPresets.IMS_PACKAGES_PLACEHOLDER, it) }
            }
        }
    }

    private suspend fun discoverImsPackages(slots: List<Int>): List<String> =
        slots.flatMap { slot ->
            listOf(BuiltInPresets.GET_IMS_SERVICE_DEVICE_COMMAND, BuiltInPresets.GET_IMS_SERVICE_CARRIER_COMMAND)
                .mapNotNull { base ->
                    val result = execute(forSlot(base, slot))
                    result.stdout.lineSequence().map { it.trim() }
                        .firstOrNull { PACKAGE_NAME.matches(it) }
                        ?.takeIf { result.isSuccess }
                }
        }.distinct()

    // --- OFF --------------------------------------------------------------------------------

    private suspend fun applyOff(): TurnOutcome {
        val snapshot = state.appliedSnapshot.value
            ?: legacySnapshot()
            ?: return TurnOutcome.Failure(FailureReason.NO_PRESET)

        val restoreCommands = expandSlots(snapshot.offCommands, snapshot.slots)
        val reEnables = snapshot.disabledPackages
            .filter { pkg -> restoreCommands.none { PM_ENABLE.matchEntire(it)?.groupValues?.get(1) == pkg } }
            .map { pkg -> "pm enable $pkg" }

        val results = (reEnables + restoreCommands).map { command ->
            execute(resolveRestoreCommand(command, snapshot))
        }
        val changed = results.any { it.isSuccess }
        if (changed) state.markOff()
        return outcomeOf(results, changed)
    }

    /**
     * State saved by app versions before 0.2.0 has no snapshot: fall back to the active preset
     * and re-enable every package its ON commands may have disabled. For presets that disabled
     * auto-discovered IMS services, all disabled IMS packages are re-enabled.
     */
    private suspend fun legacySnapshot(): AppliedSnapshot? {
        val preset = resolveActivePreset() ?: return null
        val explicit = preset.onCommands.mapNotNull { PM_DISABLE.matchEntire(it)?.groupValues?.get(1) }
        val discovered = if (preset.onCommands.any { it.contains(BuiltInPresets.IMS_PACKAGES_PLACEHOLDER) }) {
            listDisabledPackages().filter { it.contains(IMS_KEYWORD, ignoreCase = true) }
        } else {
            emptyList()
        }
        return AppliedSnapshot(
            presetId = preset.id,
            offCommands = preset.offCommands,
            slots = state.simSlotMode.value.slots,
            settingsOriginals = emptyMap(),
            disabledPackages = (explicit + discovered).distinct(),
            bootCount = AppliedSnapshot.BOOT_COUNT_UNKNOWN,
            appliedAtMs = 0L
        )
    }

    private fun resolveRestoreCommand(command: String, snapshot: AppliedSnapshot): String {
        SETTINGS_PUT.matchEntire(command)?.let { match ->
            val (namespace, key) = match.destructured
            val id = "$namespace/$key"
            if (id in snapshot.settingsOriginals) {
                val original = snapshot.settingsOriginals[id]
                return if (original == null) "settings delete $namespace $key" else "settings put $namespace $key $original"
            }
        }
        if (command.contains(BuiltInPresets.MASK_PLACEHOLDER)) {
            val slot = SLOT_ARGUMENT.find(command)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val mask = state.getSavedNetworkMask(slot)
                ?: state.getSavedNetworkMask(0)
                ?: NetworkMask.FALLBACK_ALL.also { logNote(command, NOTE_MASK_FALLBACK) }
            return command.replace(BuiltInPresets.MASK_PLACEHOLDER, mask)
        }
        return command
    }

    // --- Helpers ----------------------------------------------------------------------------

    private suspend fun exclusive(block: suspend () -> TurnOutcome): TurnOutcome {
        if (!mutex.tryLock()) return TurnOutcome.Failure(FailureReason.BUSY)
        busyFlow.value = true
        try {
            if (!shell.awaitReady(readyTimeoutMs)) return TurnOutcome.Failure(FailureReason.NOT_READY)
            return block()
        } finally {
            busyFlow.value = false
            mutex.unlock()
        }
    }

    private fun resolveActivePreset(): Preset? =
        presets.getPreset(state.activePresetId.value) ?: presets.getPreset(BuiltInPresets.DEFAULT_ID)

    private suspend fun listDisabledPackages(): Set<String> {
        val result = execute(LIST_DISABLED_PACKAGES_COMMAND)
        if (!result.isSuccess) return emptySet()
        return result.stdout.lineSequence()
            .map { it.trim().removePrefix(PACKAGE_PREFIX) }
            .filter { PACKAGE_NAME.matches(it) }
            .toSet()
    }

    private suspend fun execute(command: String): CommandResult {
        val result = try {
            shell.execute(command)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            CommandResult(command, "", error.message ?: error.javaClass.simpleName, EXIT_FAILURE)
        }
        state.appendLog(
            CommandLogEntry(clock(), result.command, result.stdout, result.stderr, result.exitCode)
        )
        return result
    }

    private fun logNote(command: String, note: String) {
        state.appendLog(CommandLogEntry(clock(), command, note, "", EXIT_NOTE))
    }

    private fun outcomeOf(results: List<CommandResult>, changed: Boolean): TurnOutcome = when {
        !changed -> TurnOutcome.Failure(FailureReason.ALL_COMMANDS_FAILED, results)
        results.all { it.isSuccess } -> TurnOutcome.Success(results)
        else -> TurnOutcome.Partial(results)
    }

    companion object {
        const val DEFAULT_READY_TIMEOUT_MS = 6_000L
        const val EXIT_NOTE = -2

        const val NOTE_MASK_FALLBACK =
            "Original network mask unavailable — the full set of standard network types will be restored."
        const val NOTE_NO_IMS_PACKAGES = "No bound IMS service found — nothing to disable."

        private const val EXIT_FAILURE = -1
        private const val IMS_KEYWORD = "ims"
        private const val SETTINGS_NULL = "null"
        private const val PACKAGE_PREFIX = "package:"
        private const val DISABLED_STATE_MARKER = "disabled"
        private const val LIST_DISABLED_PACKAGES_COMMAND = "pm list packages -d"

        private val SLOT_MARKER = Regex("(?<=\\s)-s 0(?=\\s|$)")
        private val SLOT_ARGUMENT = Regex("(?<=\\s)-s (\\d+)(?=\\s|$)")
        private val SETTINGS_PUT = Regex("^settings put (global|secure|system) (\\S+) (\\S+)(\\s*\\|\\|\\s*true)?\\s*$")
        private val PM_DISABLE = Regex("^pm disable-user(?: --user \\d+)? ([A-Za-z][\\w.]*)(?:\\s*\\|\\|\\s*true)?\\s*$")
        private val PM_ENABLE = Regex("^pm enable(?: --user \\d+)? ([A-Za-z][\\w.]*)(?:\\s*\\|\\|\\s*true)?\\s*$")
        private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

        private val DIAGNOSTICS_EXTRA_COMMANDS = listOf(
            "pm list packages | grep -i ims",
            "settings list global | grep -i -E \"volte|ims|preferred_network\"",
            "settings list secure | grep -i -E \"volte|ims\"",
            "settings list system | grep -i -E \"volte|ims\""
        )

        fun expandSlots(commands: List<String>, slots: List<Int>): List<String> =
            commands.flatMap { command ->
                if (SLOT_MARKER.containsMatchIn(command)) {
                    slots.map { slot -> forSlot(command, slot) }
                } else {
                    listOf(command)
                }
            }

        fun forSlot(command: String, slot: Int): String = command.replace(SLOT_MARKER, "-s $slot")
    }
}
