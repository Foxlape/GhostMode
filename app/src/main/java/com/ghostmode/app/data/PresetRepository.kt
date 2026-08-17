package com.ghostmode.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

class PresetRepository(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private var customPresets: List<Preset> = emptyList()
    private val presetsState = MutableStateFlow(emptyList<Preset>())

    val presets: StateFlow<List<Preset>> = presetsState

    init {
        customPresets = loadCustomPresets()
        publishPresets()
    }

    fun getPreset(presetId: String): Preset? =
        BuiltInPresets.ALL.firstOrNull { it.id == presetId }
            ?: customPresets.firstOrNull { it.id == presetId }

    fun saveCustomPreset(preset: Preset): Preset {
        if (preset.isBuiltIn) {
            throw IllegalArgumentException(BUILT_IN_SAVE_REJECTION)
        }
        val savedPreset = resolveSavedPreset(preset)
        upsertCustomPreset(savedPreset)
        return savedPreset
    }

    fun deleteCustomPreset(presetId: String) {
        if (customPresets.none { it.id == presetId }) {
            return
        }
        customPresets = customPresets.filterNot { it.id == presetId }
        persistCustomPresets()
        publishPresets()
    }

    fun exportCustomPresetsJson(): String {
        val jsonArray = JSONArray()
        customPresets.forEach { preset -> jsonArray.put(preset.toJson()) }
        return jsonArray.toString()
    }

    fun importPresetsJson(json: String): Int {
        return try {
            importParsedPresets(json)
        } catch (_: JSONException) {
            IMPORT_PARSE_ERROR
        }
    }

    private fun importParsedPresets(json: String): Int {
        val jsonArray = JSONArray(json)
        val importedPresets = buildList {
            for (index in 0 until jsonArray.length()) {
                jsonArray.getJSONObject(index).toImportedPresetOrNull()?.let { add(it) }
            }
        }
        customPresets = customPresets + importedPresets
        persistCustomPresets()
        publishPresets()
        return importedPresets.size
    }

    fun duplicatePreset(sourcePresetId: String, newTitle: String): Preset? {
        val sourcePreset = getPreset(sourcePresetId) ?: return null
        val duplicate = Preset(
            id = CUSTOM_ID_PREFIX + UUID.randomUUID(),
            title = newTitle,
            description = duplicateDescription(sourcePreset),
            onCommands = sourcePreset.onCommands,
            offCommands = sourcePreset.offCommands,
            networkMaskCaptureCommand = sourcePreset.networkMaskCaptureCommand,
            isBuiltIn = false
        )
        customPresets = customPresets + duplicate
        persistCustomPresets()
        publishPresets()
        return duplicate
    }

    private fun resolveSavedPreset(preset: Preset): Preset {
        val isExistingCustom = customPresets.any { it.id == preset.id }
        val resolvedId = if (isExistingCustom) preset.id else CUSTOM_ID_PREFIX + UUID.randomUUID()
        return preset.copy(id = resolvedId, isBuiltIn = false)
    }

    private fun upsertCustomPreset(preset: Preset) {
        val existingIndex = customPresets.indexOfFirst { it.id == preset.id }
        customPresets = if (existingIndex == INDEX_NOT_FOUND) {
            customPresets + preset
        } else {
            customPresets.mapIndexed { index, current -> if (index == existingIndex) preset else current }
        }
        persistCustomPresets()
        publishPresets()
    }

    private fun duplicateDescription(sourcePreset: Preset): String =
        if (sourcePreset.description.isBlank()) {
            EMPTY_DESCRIPTION
        } else {
            DUPLICATE_DESCRIPTION_PREFIX + sourcePreset.title
        }

    private fun publishPresets() {
        presetsState.value = BuiltInPresets.ALL + customPresets.sortedBy { it.title }
    }

    private fun loadCustomPresets(): List<Preset> {
        val storedJson = preferences.getString(PRESETS_STORAGE_KEY, null) ?: return emptyList()
        return try {
            parseCustomPresets(storedJson)
        } catch (error: JSONException) {
            emptyList()
        }
    }

    private fun parseCustomPresets(storedJson: String): List<Preset> {
        val jsonArray = JSONArray(storedJson)
        return buildList {
            for (index in 0 until jsonArray.length()) {
                add(jsonArray.getJSONObject(index).toPreset())
            }
        }
    }

    private fun persistCustomPresets() {
        val jsonArray = JSONArray()
        customPresets.forEach { preset -> jsonArray.put(preset.toJson()) }
        preferences.edit().putString(PRESETS_STORAGE_KEY, jsonArray.toString()).apply()
    }

    private fun Preset.toJson(): JSONObject {
        val jsonObject = JSONObject()
            .put(KEY_ID, id)
            .put(KEY_TITLE, title)
            .put(KEY_DESCRIPTION, description)
            .put(KEY_ON_COMMANDS, JSONArray(onCommands))
            .put(KEY_OFF_COMMANDS, JSONArray(offCommands))
        networkMaskCaptureCommand?.let { jsonObject.put(KEY_NETWORK_MASK_CAPTURE_COMMAND, it) }
        return jsonObject
    }

    private fun JSONObject.toPreset(): Preset =
        Preset(
            id = getString(KEY_ID),
            title = getString(KEY_TITLE),
            description = optString(KEY_DESCRIPTION, EMPTY_DESCRIPTION),
            onCommands = toStringList(getJSONArray(KEY_ON_COMMANDS)),
            offCommands = toStringList(getJSONArray(KEY_OFF_COMMANDS)),
            networkMaskCaptureCommand = optStringOrNull(KEY_NETWORK_MASK_CAPTURE_COMMAND),
            isBuiltIn = false
        )

    private fun JSONObject.toImportedPresetOrNull(): Preset? {
        val importedPreset = try {
            Preset(
                id = CUSTOM_ID_PREFIX + UUID.randomUUID(),
                title = getString(KEY_TITLE),
                description = optString(KEY_DESCRIPTION, EMPTY_DESCRIPTION),
                onCommands = toStringList(getJSONArray(KEY_ON_COMMANDS)),
                offCommands = toStringList(getJSONArray(KEY_OFF_COMMANDS)),
                networkMaskCaptureCommand = optStringOrNull(KEY_NETWORK_MASK_CAPTURE_COMMAND),
                isBuiltIn = false
            )
        } catch (_: JSONException) {
            return null
        }
        if (importedPreset.onCommands.isEmpty() || importedPreset.offCommands.isEmpty()) return null
        return importedPreset
    }

    private fun toStringList(jsonArray: JSONArray): List<String> =
        buildList {
            for (index in 0 until jsonArray.length()) {
                add(jsonArray.getString(index))
            }
        }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key)) getString(key) else null

    private companion object {
        const val PREFERENCES_NAME = "ghost_presets"
        const val PRESETS_STORAGE_KEY = "custom_presets"
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_DESCRIPTION = "description"
        const val KEY_ON_COMMANDS = "onCommands"
        const val KEY_OFF_COMMANDS = "offCommands"
        const val KEY_NETWORK_MASK_CAPTURE_COMMAND = "networkMaskCaptureCommand"
        const val CUSTOM_ID_PREFIX = "custom_"
        const val DUPLICATE_DESCRIPTION_PREFIX = "Копия пресета "
        const val EMPTY_DESCRIPTION = ""
        const val BUILT_IN_SAVE_REJECTION = "Built-in presets cannot be saved as custom presets"
        const val INDEX_NOT_FOUND = -1
        const val IMPORT_PARSE_ERROR = -1
    }
}
