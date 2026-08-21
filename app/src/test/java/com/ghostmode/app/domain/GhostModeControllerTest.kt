package com.ghostmode.app.domain

import com.ghostmode.app.data.BuiltInPresets
import com.ghostmode.app.data.CommandLogEntry
import com.ghostmode.app.data.GhostSession
import com.ghostmode.app.data.GhostStateRepository
import com.ghostmode.app.data.Preset
import com.ghostmode.app.data.PresetRepository
import com.ghostmode.app.data.SimSlotMode
import com.ghostmode.app.shell.CommandResult
import com.ghostmode.app.shell.ShellExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeShellExecutor : ShellExecutor {
    var isReady = true
    val executedCommands = mutableListOf<String>()
    var commandHandler: (String) -> CommandResult = { CommandResult(it, "success", "", 0) }

    private val readinessFlow = MutableStateFlow(true)
    override val readiness: StateFlow<Boolean> = readinessFlow.asStateFlow()

    fun setReadyState(ready: Boolean) {
        isReady = ready
        readinessFlow.value = ready
    }

    override suspend fun execute(command: String): CommandResult {
        executedCommands.add(command)
        return commandHandler(command)
    }
}

class FakePresetRepository : PresetRepository(null) {
    private val presetsMap = mutableMapOf<String, Preset>()

    init {
        BuiltInPresets.ALL.forEach { presetsMap[it.id] = it }
    }

    override val presets: StateFlow<List<Preset>> = MutableStateFlow(BuiltInPresets.ALL).asStateFlow()

    override fun getPreset(presetId: String): Preset? = presetsMap[presetId]
    override fun saveCustomPreset(preset: Preset): Preset {
        presetsMap[preset.id] = preset
        return preset
    }
    override fun deleteCustomPreset(presetId: String) { presetsMap.remove(presetId) }
}

class FakeGhostStateRepository : GhostStateRepository(null) {
    private val isOnFlow = MutableStateFlow(false)
    private val savedMaskFlow = MutableStateFlow<String?>(null)
    private val savedMaskSlot1Flow = MutableStateFlow<String?>(null)
    private val simSlotModeFlow = MutableStateFlow(SimSlotMode.ALL)
    private val activePresetIdFlow = MutableStateFlow(BuiltInPresets.DEFAULT_ID)
    private val logs = mutableListOf<CommandLogEntry>()

    override val isOn: StateFlow<Boolean> = isOnFlow.asStateFlow()
    override val isOnTimestampMs: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()
    override val savedNetworkMask: StateFlow<String?> = savedMaskFlow.asStateFlow()
    override val savedNetworkMaskSlot1: StateFlow<String?> = savedMaskSlot1Flow.asStateFlow()
    override val savedMaskTimestampMs: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()
    override val simSlotMode: StateFlow<SimSlotMode> = simSlotModeFlow.asStateFlow()
    override val activePresetId: StateFlow<String> = activePresetIdFlow.asStateFlow()
    override val logEntries: StateFlow<List<CommandLogEntry>> = MutableStateFlow(emptyList<CommandLogEntry>()).asStateFlow()
    override val notificationEnabled: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
    override val sessions: StateFlow<List<GhostSession>> = MutableStateFlow(emptyList<GhostSession>()).asStateFlow()
    override val scheduleEnabled: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val scheduleStartMinuteOfDay: StateFlow<Int> = MutableStateFlow(1380).asStateFlow()
    override val scheduleEndMinuteOfDay: StateFlow<Int> = MutableStateFlow(480).asStateFlow()

    override fun setIsOn(value: Boolean) { isOnFlow.value = value }
    override fun setNotificationEnabled(value: Boolean) {}
    override fun setScheduleEnabled(value: Boolean) {}
    override fun setScheduleStartMinuteOfDay(minuteOfDay: Int) {}
    override fun setScheduleEndMinuteOfDay(minuteOfDay: Int) {}
    override fun setSimSlotMode(mode: SimSlotMode) { simSlotModeFlow.value = mode }
    override fun setSavedNetworkMask(mask: String?) { savedMaskFlow.value = mask }
    override fun setSavedNetworkMaskForSlot(slot: Int, mask: String?) {
        if (slot == 1) savedMaskSlot1Flow.value = mask else savedMaskFlow.value = mask
    }
    override fun getSavedNetworkMaskForSlot(slot: Int): String? =
        if (slot == 1) savedMaskSlot1Flow.value else savedMaskFlow.value
    override fun setActivePresetId(presetId: String) { activePresetIdFlow.value = presetId }
    override fun appendLog(entry: CommandLogEntry) { logs.add(entry) }
    override fun removeLogEntry(timestampMs: Long) {}
    override fun clearLog() { logs.clear() }
}

class GhostModeControllerTest {

    private lateinit var fakeShell: FakeShellExecutor
    private lateinit var fakePresetRepo: FakePresetRepository
    private lateinit var fakeStateRepo: FakeGhostStateRepository
    private lateinit var controller: GhostModeController

    @Before
    fun setUp() {
        fakeShell = FakeShellExecutor()
        fakePresetRepo = FakePresetRepository()
        fakeStateRepo = FakeGhostStateRepository()
        controller = GhostModeController(fakeShell, fakePresetRepo, fakeStateRepo)
    }

    @Test
    fun turnOn_success_setsIsOnTrue() = runTest {
        val outcome = controller.turnOn()
        assertTrue(outcome is TurnOutcome.Success)
        assertTrue(fakeStateRepo.isOn.value)
        assertTrue(fakeShell.executedCommands.isNotEmpty())
    }

    @Test
    fun turnOff_success_setsIsOnFalse() = runTest {
        fakeStateRepo.setIsOn(true)
        fakeStateRepo.setSavedNetworkMask("11001111101111111111")
        val outcome = controller.turnOff()
        assertTrue(outcome is TurnOutcome.Success)
        assertFalse(fakeStateRepo.isOn.value)
    }

    @Test
    fun turnOn_whenShellNotReady_returnsFailure() = runTest {
        fakeShell.setReadyState(false)
        val outcome = controller.turnOn()
        assertTrue(outcome is TurnOutcome.Failure)
        assertFalse(fakeStateRepo.isOn.value)
    }

    @Test
    fun turnOn_withSim1_executesOnlySlot0Commands() = runTest {
        fakeStateRepo.setSimSlotMode(SimSlotMode.SIM_1)
        controller.turnOn()
        val imsCommands = fakeShell.executedCommands.filter { it.contains("ims disable") }
        assertTrue(imsCommands.any { it.contains("-s 0") })
        assertFalse(imsCommands.any { it.contains("-s 1") })
    }

    @Test
    fun turnOn_withSim2_executesOnlySlot1Commands() = runTest {
        fakeStateRepo.setSimSlotMode(SimSlotMode.SIM_2)
        controller.turnOn()
        val imsCommands = fakeShell.executedCommands.filter { it.contains("ims disable") }
        assertTrue(imsCommands.any { it.contains("-s 1") })
        assertFalse(imsCommands.any { it.contains("-s 0") })
    }

    @Test
    fun turnOn_withAllSims_executesBothSlot0AndSlot1Commands() = runTest {
        fakeStateRepo.setSimSlotMode(SimSlotMode.ALL)
        controller.turnOn()
        val imsCommands = fakeShell.executedCommands.filter { it.contains("ims disable") }
        assertTrue(imsCommands.any { it.contains("-s 0") })
        assertTrue(imsCommands.any { it.contains("-s 1") })
    }
}
