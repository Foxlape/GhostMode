package com.ghostmode.app.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Everything needed to undo exactly what the last "turn on" changed, independent of the
 * preset / SIM selection the user may have changed afterwards.
 *
 * @property settingsOriginals original values of `settings` keys touched by the ON commands,
 *   keyed as `namespace/key`; a `null` value means the key did not exist.
 * @property disabledPackages packages that were enabled before and got disabled by the ON
 *   commands; they are re-enabled on OFF, packages the user disabled on purpose stay untouched.
 * @property bootCount `Settings.Global.BOOT_COUNT` at the moment the commands were applied,
 *   used to detect that a reboot reverted the non-persistent part of the state.
 */
data class AppliedSnapshot(
    val presetId: String,
    val offCommands: List<String>,
    val slots: List<Int>,
    val settingsOriginals: Map<String, String?>,
    val disabledPackages: List<String>,
    val bootCount: Int,
    val appliedAtMs: Long
) {
    fun toJson(): String = JSONObject()
        .put(KEY_PRESET_ID, presetId)
        .put(KEY_OFF_COMMANDS, JSONArray(offCommands))
        .put(KEY_SLOTS, JSONArray(slots))
        .put(KEY_SETTINGS, JSONObject().apply {
            settingsOriginals.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
        })
        .put(KEY_DISABLED_PACKAGES, JSONArray(disabledPackages))
        .put(KEY_BOOT_COUNT, bootCount)
        .put(KEY_APPLIED_AT, appliedAtMs)
        .toString()

    companion object {
        const val BOOT_COUNT_UNKNOWN = -1

        private const val KEY_PRESET_ID = "presetId"
        private const val KEY_OFF_COMMANDS = "offCommands"
        private const val KEY_SLOTS = "slots"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_DISABLED_PACKAGES = "disabledPackages"
        private const val KEY_BOOT_COUNT = "bootCount"
        private const val KEY_APPLIED_AT = "appliedAtMs"

        fun fromJson(json: String?): AppliedSnapshot? {
            if (json.isNullOrBlank()) return null
            return try {
                val root = JSONObject(json)
                val offArray = root.getJSONArray(KEY_OFF_COMMANDS)
                val slotsArray = root.getJSONArray(KEY_SLOTS)
                val settingsJson = root.optJSONObject(KEY_SETTINGS) ?: JSONObject()
                val packagesArray = root.optJSONArray(KEY_DISABLED_PACKAGES) ?: JSONArray()
                AppliedSnapshot(
                    presetId = root.getString(KEY_PRESET_ID),
                    offCommands = List(offArray.length()) { offArray.getString(it) },
                    slots = List(slotsArray.length()) { slotsArray.getInt(it) },
                    settingsOriginals = settingsJson.keys().asSequence().associateWith { key ->
                        if (settingsJson.isNull(key)) null else settingsJson.getString(key)
                    },
                    disabledPackages = List(packagesArray.length()) { packagesArray.getString(it) },
                    bootCount = root.optInt(KEY_BOOT_COUNT, BOOT_COUNT_UNKNOWN),
                    appliedAtMs = root.optLong(KEY_APPLIED_AT, 0L)
                )
            } catch (_: JSONException) {
                null
            }
        }
    }
}
