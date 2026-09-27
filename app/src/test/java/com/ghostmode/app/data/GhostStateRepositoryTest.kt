package com.ghostmode.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostStateRepositoryTest {

    private var now = 1_000_000L
    private val store = InMemoryStore()
    private val repository = GhostStateRepository(store) { now }

    private fun snapshot(bootCount: Int = 3) = AppliedSnapshot(
        presetId = BuiltInPresets.ID_STOCK_PIXEL,
        offCommands = listOf("cmd phone ims enable -s 0"),
        slots = listOf(0),
        settingsOriginals = mapOf("global/preferred_network_mode" to "33", "global/volte_vt_enabled" to null),
        disabledPackages = listOf("com.example.ims"),
        bootCount = bootCount,
        appliedAtMs = 1_000_000L
    )

    @Test
    fun markOn_thenOff_recordsOneClosedSession() {
        repository.markOn(snapshot())
        now += 60_000L
        repository.markOff()

        assertFalse(repository.isOn.value)
        assertNull(repository.appliedSnapshot.value)
        assertEquals(listOf(GhostSession(1_000_000L, 1_060_000L)), repository.sessions.value)
    }

    @Test
    fun reapply_keepsSessionStartAndDoesNotOpenSecondSession() {
        repository.markOn(snapshot(bootCount = 3))
        val since = repository.isOnTimestampMs.value
        now += 5_000L
        repository.markOn(snapshot(bootCount = 4))

        assertEquals(since, repository.isOnTimestampMs.value)
        assertEquals(1, repository.sessions.value.size)
        assertEquals(4, repository.appliedSnapshot.value?.bootCount)
    }

    @Test
    fun markOff_clearsTimer() {
        repository.markOn(snapshot())
        repository.setTimerFireAtMs(now + 1000)
        repository.markOff()

        assertEquals(GhostStateRepository.NONE, repository.timerFireAtMs.value)
    }

    @Test
    fun stateSurvivesReload() {
        repository.markOn(snapshot())
        repository.setSavedNetworkMask(1, "0101")
        repository.setSchedule(true, 22 * 60, 7 * 60)
        repository.setSimSlotMode(SimSlotMode.SIM_2)

        val reloaded = GhostStateRepository(store) { now }

        assertTrue(reloaded.isOn.value)
        assertEquals(snapshot(), reloaded.appliedSnapshot.value)
        assertEquals("0101", reloaded.getSavedNetworkMask(1))
        assertTrue(reloaded.scheduleEnabled.value)
        assertEquals(22 * 60, reloaded.scheduleStartMinuteOfDay.value)
        assertEquals(SimSlotMode.SIM_2, reloaded.simSlotMode.value)
        assertEquals(1, reloaded.sessions.value.size)
    }

    @Test
    fun log_isCappedAndClearable() {
        repeat(GhostStateRepository.LOG_CAPACITY + 10) { index ->
            repository.appendLog(CommandLogEntry(index.toLong(), "cmd $index", "", "", 0))
        }
        assertEquals(GhostStateRepository.LOG_CAPACITY, repository.logEntries.value.size)
        assertEquals("cmd 10", repository.logEntries.value.first().command)

        repository.clearLog()
        assertTrue(repository.logEntries.value.isEmpty())
    }
}
