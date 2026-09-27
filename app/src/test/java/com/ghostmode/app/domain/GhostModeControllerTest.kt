package com.ghostmode.app.domain

import com.ghostmode.app.data.BuiltInPresets
import com.ghostmode.app.data.GhostStateRepository
import com.ghostmode.app.data.InMemoryStore
import com.ghostmode.app.data.KeyValueStore
import com.ghostmode.app.data.PresetRepository
import com.ghostmode.app.data.SimSlotMode
import com.ghostmode.app.shell.CommandResult
import com.ghostmode.app.shell.ShellExecutor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Scriptable shell: the most recently added matching rule answers; anything else succeeds with empty output. */
class FakeShell : ShellExecutor {
    val executed = mutableListOf<String>()
    var ready = true
    var gate: CompletableDeferred<Unit>? = null
    private val rules = mutableListOf<Pair<Regex, (String) -> CommandResult>>()

    override val readiness: StateFlow<Boolean> = MutableStateFlow(true)

    override suspend fun awaitReady(timeoutMs: Long): Boolean = ready

    fun on(pattern: String, stdout: String = "", exitCode: Int = 0) {
        rules += Regex(pattern) to { command -> CommandResult(command, stdout, "", exitCode) }
    }

    override suspend fun execute(command: String): CommandResult {
        gate?.await()
        executed += command
        return rules.lastOrNull { (regex, _) -> regex.containsMatchIn(command) }?.second?.invoke(command)
            ?: CommandResult(command, "", "", 0)
    }

    fun executedMatching(pattern: String): List<String> = executed.filter { Regex(pattern).containsMatchIn(it) }
}

class GhostModeControllerTest {

    private lateinit var shell: FakeShell
    private lateinit var store: KeyValueStore
    private lateinit var state: GhostStateRepository
    private lateinit var presets: PresetRepository
    private var bootCount = 7
    private lateinit var controller: GhostModeController

    @Before
    fun setUp() {
        shell = FakeShell()
        store = InMemoryStore()
        state = GhostStateRepository(store)
        presets = PresetRepository(InMemoryStore())
        controller = GhostModeController(shell, presets, state, bootCount = { bootCount })
        shell.on("get-allowed-network-types", stdout = ORIGINAL_MASK)
    }

    @Test
    fun turnOn_marksOnAndStoresSnapshot() = runTest {
        val outcome = controller.turnOn()

        assertTrue(outcome is TurnOutcome.Success)
        assertTrue(state.isOn.value)
        val snapshot = state.appliedSnapshot.value
        assertNotNull(snapshot)
        snapshot!!
        assertEquals(BuiltInPresets.DEFAULT_ID, snapshot.presetId)
        assertEquals(listOf(0, 1), snapshot.slots)
        assertEquals(7, snapshot.bootCount)
        assertEquals(ORIGINAL_MASK, state.getSavedNetworkMask(0))
    }

    @Test
    fun turnOn_whenBackendMissing_changesNothing() = runTest {
        shell.ready = false

        val outcome = controller.turnOn()

        assertEquals(TurnOutcome.Failure(FailureReason.NOT_READY), outcome)
        assertFalse(state.isOn.value)
        assertTrue(shell.executed.isEmpty())
    }

    @Test
    fun turnOn_whenEveryCommandFails_staysOff() = runTest {
        shell.on(".*", exitCode = 1)

        val outcome = controller.turnOn()

        assertTrue(outcome is TurnOutcome.Failure)
        assertEquals(FailureReason.ALL_COMMANDS_FAILED, (outcome as TurnOutcome.Failure).reason)
        assertFalse(state.isOn.value)
        assertNull(state.appliedSnapshot.value)
    }

    @Test
    fun turnOn_withSomeFailures_isPartial() = runTest {
        shell.on("ims disable", exitCode = 1)

        val outcome = controller.turnOn()

        assertTrue(outcome is TurnOutcome.Partial)
        assertTrue(state.isOn.value)
    }

    @Test
    fun turnOn_sim2_targetsOnlySlot1() = runTest {
        state.setSimSlotMode(SimSlotMode.SIM_2)
        state.setActivePresetId(BuiltInPresets.ID_STOCK_PIXEL)

        controller.turnOn()

        val imsCommands = shell.executedMatching("ims disable")
        assertEquals(listOf("cmd phone ims disable -s 1"), imsCommands)
    }

    @Test
    fun turnOff_restoresCapturedMaskPerSlot() = runTest {
        state.setActivePresetId(BuiltInPresets.ID_STOCK_PIXEL)
        controller.turnOn()
        shell.executed.clear()

        val outcome = controller.turnOff()

        assertTrue(outcome is TurnOutcome.Success)
        assertFalse(state.isOn.value)
        assertNull(state.appliedSnapshot.value)
        assertEquals(
            listOf(
                "cmd phone set-allowed-network-types-for-users -s 0 $ORIGINAL_MASK",
                "cmd phone set-allowed-network-types-for-users -s 1 $ORIGINAL_MASK"
            ),
            shell.executedMatching("set-allowed-network-types")
        )
    }

    @Test
    fun turnOff_usesAppliedPreset_evenIfSelectionChangedAfterwards() = runTest {
        state.setActivePresetId(BuiltInPresets.ID_SAMSUNG_ONE_UI)
        shell.on("pm disable-user", stdout = "Package new state: disabled-user")
        controller.turnOn()
        // Simulates state written by an old version / another component while the mode is on.
        state.setActivePresetId(BuiltInPresets.ID_STOCK_PIXEL)
        state.setSimSlotMode(SimSlotMode.SIM_1)
        shell.executed.clear()

        controller.turnOff()

        assertTrue(shell.executed.contains("pm enable ${BuiltInPresets.SAMSUNG_IMS_PACKAGE}"))
        assertTrue(shell.executed.any { it.startsWith("settings put global volte_vt_enabled") })
        assertEquals(2, shell.executedMatching("set-allowed-network-types").size)
    }

    @Test
    fun settingsKeys_areRestoredToOriginalValues() = runTest {
        state.setActivePresetId(BuiltInPresets.ID_SAMSUNG_ONE_UI)
        shell.on("settings get global preferred_network_mode", stdout = "33\n")
        shell.on("settings get global preferred_network_mode2", stdout = "null\n")
        shell.on("settings get global volte_vt_enabled", stdout = "0\n")
        controller.turnOn()
        shell.executed.clear()

        controller.turnOff()

        assertTrue(shell.executed.contains("settings put global preferred_network_mode 33"))
        assertTrue(shell.executed.contains("settings delete global preferred_network_mode2"))
        // VoLTE was disabled by the user before: it must stay disabled.
        assertTrue(shell.executed.contains("settings put global volte_vt_enabled 0"))
        assertFalse(shell.executed.any { it.contains(BuiltInPresets.NETWORK_MODE_RESTORE_FALLBACK) && it.contains("preferred_network_mode ") })
    }

    @Test
    fun discoveredImsPackage_isDisabledAndReEnabled() = runTest {
        shell.on("get-ims-service -s 0 -d", stdout = "com.shannon.imsservice\n")
        shell.on("pm list packages -d", stdout = "package:com.example.disabled\n")
        shell.on("pm disable-user", stdout = "Package com.shannon.imsservice new state: disabled-user")
        controller.turnOn()

        assertTrue(shell.executed.contains("pm disable-user --user 0 com.shannon.imsservice || true"))
        assertEquals(listOf("com.shannon.imsservice"), state.appliedSnapshot.value?.disabledPackages)

        shell.executed.clear()
        controller.turnOff()

        assertEquals("pm enable com.shannon.imsservice", shell.executed.first())
    }

    @Test
    fun packagesDisabledBeforehand_areNotReEnabled() = runTest {
        state.setActivePresetId(BuiltInPresets.ID_ONEPLUS)
        shell.on("pm list packages -d", stdout = "package:${BuiltInPresets.QUALCOMM_IMS_PACKAGE}\n")
        shell.on("pm disable-user", stdout = "new state: disabled-user")
        shell.on("pm disable-user --user 0 ${BuiltInPresets.MEDIATEK_IMS_PACKAGE.replace(".", "\\.")}", stdout = "Unknown package")
        controller.turnOn()
        shell.executed.clear()

        controller.turnOff()

        assertTrue(shell.executedMatching("^pm enable").isEmpty())
    }

    @Test
    fun maskCapture_isFreshEachSession_butNeverTrustsLteOnly() = runTest {
        state.setActivePresetId(BuiltInPresets.ID_STOCK_PIXEL)
        controller.turnOn()
        controller.turnOff()

        // Next session: the modem is still (or again) LTE-only — keep the known good mask.
        val fresh = FakeShell().apply { on("get-allowed-network-types", stdout = NetworkMask.LTE_ONLY) }
        val second = GhostModeController(fresh, presets, state, bootCount = { bootCount })
        second.turnOn()

        assertEquals(ORIGINAL_MASK, state.getSavedNetworkMask(0))
    }

    @Test
    fun maskCapture_acceptsNetworkTypeNames() = runTest {
        val namesShell = FakeShell().apply { on("get-allowed-network-types", stdout = "GSM|UMTS|LTE|NR") }
        GhostModeController(namesShell, presets, state).turnOn()

        assertEquals(NetworkMask.fromTypes(setOf(16, 3, 13, 20)), state.getSavedNetworkMask(0))
    }

    @Test
    fun needsReapply_afterBootCountChanges() = runTest {
        controller.turnOn()
        assertFalse(controller.needsReapply())

        bootCount = 8
        assertTrue(controller.needsReapply())

        controller.reapply()
        assertFalse(controller.needsReapply())
        assertTrue(state.isOn.value)
    }

    @Test
    fun reapply_keepsOriginalSettingsInsteadOfRecapturing() = runTest {
        state.setActivePresetId(BuiltInPresets.ID_SAMSUNG_ONE_UI)
        shell.on("settings get global preferred_network_mode", stdout = "33")
        controller.turnOn()

        bootCount = 8
        // After the first ON the key reads as LTE-only; re-applying must not overwrite the original.
        val afterBoot = FakeShell().apply { on("settings get", stdout = "11") }
        GhostModeController(afterBoot, presets, state, bootCount = { bootCount }).reapply()

        assertEquals("33", state.appliedSnapshot.value?.settingsOriginals?.get("global/preferred_network_mode"))
        assertTrue(afterBoot.executedMatching("settings get").isEmpty())
    }

    @Test
    fun legacyOnState_withoutSnapshot_restoresPresetPackages() = runTest {
        // State written by 0.1.x: ON flag without snapshot.
        store.edit { putBoolean("is_on", true) }
        store.edit { putString("active_preset_id", BuiltInPresets.ID_SAMSUNG_ONE_UI) }
        val legacyState = GhostStateRepository(store)
        val legacyController = GhostModeController(shell, presets, legacyState)

        legacyController.turnOff()

        assertTrue(shell.executed.contains("pm enable ${BuiltInPresets.SAMSUNG_IMS_PACKAGE}"))
        assertTrue(shell.executed.contains("pm enable ${BuiltInPresets.SAMSUNG_IMS_PACKAGE_NEW}"))
        assertFalse(legacyState.isOn.value)
    }

    @Test
    fun selectPreset_isRejectedWhileOn() = runTest {
        controller.turnOn()

        assertFalse(controller.selectPreset(BuiltInPresets.ID_STOCK_PIXEL))
        assertEquals(BuiltInPresets.DEFAULT_ID, state.activePresetId.value)
    }

    @Test
    fun concurrentCall_isRejectedAsBusy() = runTest {
        shell.gate = CompletableDeferred()
        val first = async { controller.turnOn() }
        yield()

        assertTrue(controller.isBusy.value)
        assertEquals(TurnOutcome.Failure(FailureReason.BUSY), controller.turnOff())

        shell.gate!!.complete(Unit)
        assertTrue(first.await() is TurnOutcome.Success)
    }

    @Test
    fun diagnostics_reportBlockedWhenLteOnlyAndNoIms() = runTest {
        shell.on("get-allowed-network-types", stdout = NetworkMask.LTE_ONLY)
        shell.on("pm list packages -d", stdout = "package:org.codeaurora.ims\npackage:com.other\n")

        val report = controller.runDiagnostics()
        assertNotNull(report)
        report!!

        assertTrue(report.callsLikelyBlocked)
        assertEquals(listOf("org.codeaurora.ims"), report.disabledImsPackages)
    }

    private companion object {
        const val ORIGINAL_MASK = "11001111101111111111"
    }
}
