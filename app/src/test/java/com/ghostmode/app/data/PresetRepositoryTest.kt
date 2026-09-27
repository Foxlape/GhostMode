package com.ghostmode.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetRepositoryTest {

    private val store = InMemoryStore()
    private val repository = PresetRepository(store)

    private fun custom(title: String) =
        Preset("", title, "", listOf("on"), listOf("off"), null, isBuiltIn = false)

    @Test
    fun save_assignsIdAndPersists() {
        val saved = repository.saveCustomPreset(custom("Mine"))

        assertTrue(saved.id.startsWith("custom_"))
        assertEquals(saved, PresetRepository(store).getPreset(saved.id))
        assertEquals(BuiltInPresets.ALL.size + 1, repository.presets.value.size)
    }

    @Test
    fun save_existingId_updatesInPlace() {
        val saved = repository.saveCustomPreset(custom("Mine"))
        repository.saveCustomPreset(saved.copy(title = "Renamed"))

        assertEquals("Renamed", repository.getPreset(saved.id)?.title)
        assertEquals(BuiltInPresets.ALL.size + 1, repository.presets.value.size)
    }

    @Test
    fun duplicateOfBuiltIn_becomesCustom() {
        val builtIn = repository.getPreset(BuiltInPresets.ID_SAMSUNG_ONE_UI)!!
        val copy = repository.saveCustomPreset(builtIn.copy(id = "", isBuiltIn = false, title = "Copy"))

        assertNotEquals(builtIn.id, copy.id)
        assertEquals(0, copy.titleRes)
        assertEquals(builtIn.onCommands, copy.onCommands)
    }

    @Test
    fun exportImport_roundTripSkipsDuplicates() {
        repository.saveCustomPreset(custom("A"))
        val json = repository.exportCustomPresetsJson()

        val other = PresetRepository(InMemoryStore())
        val parsed = other.parseImport(json)!!
        assertEquals(1, other.importPresets(parsed))
        assertEquals(0, other.importPresets(parsed))
    }

    @Test
    fun parseImport_rejectsInvalidEntries() {
        assertNull(repository.parseImport("not json"))
        val parsed = repository.parseImport(
            "[{\"title\":\"x\",\"onCommands\":[],\"offCommands\":[\"a\"]},{\"foo\":1}]"
        )
        assertEquals(emptyList<Preset>(), parsed)
    }

    @Test
    fun delete_removesOnlyCustom() {
        val saved = repository.saveCustomPreset(custom("A"))
        repository.deleteCustomPreset(saved.id)
        repository.deleteCustomPreset(BuiltInPresets.ID_UNIVERSAL)

        assertNull(repository.getPreset(saved.id))
        assertEquals(BuiltInPresets.ALL.size, repository.presets.value.size)
    }
}
