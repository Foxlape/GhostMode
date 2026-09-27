package com.ghostmode.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

class PresetRepository(private val store: KeyValueStore) {

    private val lock = Any()
    private var customPresets: List<Preset> = loadCustomPresets()
    private val presetsState = MutableStateFlow(allPresets())

    val presets: StateFlow<List<Preset>> = presetsState.asStateFlow()

    fun getPreset(presetId: String): Preset? =
        BuiltInPresets.ALL.firstOrNull { it.id == presetId }
            ?: customPresets.firstOrNull { it.id == presetId }

    /** Inserts a new custom preset or replaces an existing one with the same id. */
    fun saveCustomPreset(preset: Preset): Preset {
        require(!preset.isBuiltIn) { "Built-in presets are read-only" }
        synchronized(lock) {
            val isExisting = customPresets.any { it.id == preset.id }
            val saved = preset.copy(
                id = if (isExisting) preset.id else newCustomId(),
                isBuiltIn = false,
                titleRes = 0,
                descriptionRes = 0
            )
            customPresets = if (isExisting) {
                customPresets.map { if (it.id == saved.id) saved else it }
            } else {
                customPresets + saved
            }
            persistAndPublish()
            return saved
        }
    }

    fun deleteCustomPreset(presetId: String) {
        synchronized(lock) {
            if (customPresets.none { it.id == presetId }) return
            customPresets = customPresets.filterNot { it.id == presetId }
            persistAndPublish()
        }
    }

    fun exportCustomPresetsJson(): String {
        val array = JSONArray()
        customPresets.forEach { array.put(it.toJson()) }
        return array.toString(2)
    }

    /**
     * Parses presets from JSON without saving them, so the UI can show the commands to the
     * user before anything is imported. Returns `null` if the JSON is malformed.
     */
    fun parseImport(json: String): List<Preset>? = try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.toImportedPresetOrNull()
        }
    } catch (_: JSONException) {
        null
    }

    /** Adds previously parsed presets, skipping exact duplicates. Returns the number added. */
    fun importPresets(parsed: List<Preset>): Int {
        synchronized(lock) {
            val fresh = parsed.filterNot { candidate ->
                customPresets.any { existing ->
                    existing.title == candidate.title &&
                        existing.onCommands == candidate.onCommands &&
                        existing.offCommands == candidate.offCommands
                }
            }.map { it.copy(id = newCustomId(), isBuiltIn = false) }
            if (fresh.isEmpty()) return 0
            customPresets = customPresets + fresh
            persistAndPublish()
            return fresh.size
        }
    }

    private fun allPresets(): List<Preset> = BuiltInPresets.ALL + customPresets.sortedBy { it.title.lowercase() }

    private fun persistAndPublish() {
        val array = JSONArray()
        customPresets.forEach { array.put(it.toJson()) }
        store.edit { putString(STORAGE_KEY, array.toString()) }
        presetsState.value = allPresets()
    }

    private fun loadCustomPresets(): List<Preset> {
        val json = store.getString(STORAGE_KEY) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.toStoredPresetOrNull() }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun Preset.toJson(): JSONObject = JSONObject()
        .put(KEY_ID, id)
        .put(KEY_TITLE, title)
        .put(KEY_DESCRIPTION, description)
        .put(KEY_ON_COMMANDS, JSONArray(onCommands))
        .put(KEY_OFF_COMMANDS, JSONArray(offCommands))
        .apply { networkMaskCaptureCommand?.let { put(KEY_CAPTURE_COMMAND, it) } }

    private fun JSONObject.toStoredPresetOrNull(): Preset? = try {
        Preset(
            id = getString(KEY_ID),
            title = getString(KEY_TITLE),
            description = optString(KEY_DESCRIPTION, ""),
            onCommands = getJSONArray(KEY_ON_COMMANDS).toStringList(),
            offCommands = getJSONArray(KEY_OFF_COMMANDS).toStringList(),
            networkMaskCaptureCommand = optStringOrNull(KEY_CAPTURE_COMMAND),
            isBuiltIn = false
        )
    } catch (_: JSONException) {
        null
    }

    private fun JSONObject.toImportedPresetOrNull(): Preset? {
        val preset = try {
            Preset(
                id = "",
                title = getString(KEY_TITLE).trim(),
                description = optString(KEY_DESCRIPTION, "").trim(),
                onCommands = getJSONArray(KEY_ON_COMMANDS).toStringList(),
                offCommands = getJSONArray(KEY_OFF_COMMANDS).toStringList(),
                networkMaskCaptureCommand = optStringOrNull(KEY_CAPTURE_COMMAND),
                isBuiltIn = false
            )
        } catch (_: JSONException) {
            return null
        }
        return preset.takeIf { it.title.isNotEmpty() && it.onCommands.isNotEmpty() && it.offCommands.isNotEmpty() }
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it).trim() }.filter { it.isNotEmpty() }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key).trim().ifEmpty { null } else null

    private fun newCustomId(): String = CUSTOM_ID_PREFIX + UUID.randomUUID()

    companion object {
        const val PREFS_NAME = "ghost_presets"
        private const val STORAGE_KEY = "custom_presets"
        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_ON_COMMANDS = "onCommands"
        private const val KEY_OFF_COMMANDS = "offCommands"
        private const val KEY_CAPTURE_COMMAND = "networkMaskCaptureCommand"
        private const val CUSTOM_ID_PREFIX = "custom_"
    }
}
