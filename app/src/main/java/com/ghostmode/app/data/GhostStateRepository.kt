package com.ghostmode.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class CommandLogEntry(
    val timestampMs: Long,
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int
) {
    val isSuccess: Boolean get() = exitCode == 0
}

data class GhostSession(
    val startMs: Long,
    val endMs: Long
) {
    val isOpen: Boolean get() = endMs == SESSION_END_OPEN

    companion object {
        const val SESSION_END_OPEN = 0L
    }
}

/**
 * Single source of truth for persisted app state. All components (activity, tile, widget,
 * receivers) share one instance via [com.ghostmode.app.AppGraph], so flows are updated in place
 * and no cross-instance synchronisation is needed.
 */
class GhostStateRepository(
    private val store: KeyValueStore,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val isOnFlow = MutableStateFlow(store.getBoolean(KEY_IS_ON, false))
    private val isOnTimestampFlow = MutableStateFlow(store.getLong(KEY_IS_ON_TIMESTAMP, NONE))
    private val savedMaskSlot0Flow = MutableStateFlow(store.getString(KEY_SAVED_MASK))
    private val savedMaskSlot1Flow = MutableStateFlow(store.getString(KEY_SAVED_MASK_SLOT_1))
    private val simSlotModeFlow = MutableStateFlow(SimSlotMode.fromStorage(store.getString(KEY_SIM_SLOT_MODE)))
    private val activePresetIdFlow = MutableStateFlow(store.getString(KEY_ACTIVE_PRESET_ID) ?: BuiltInPresets.DEFAULT_ID)
    private val notificationEnabledFlow = MutableStateFlow(store.getBoolean(KEY_NOTIFICATION_ENABLED, false))
    private val scheduleEnabledFlow = MutableStateFlow(store.getBoolean(KEY_SCHEDULE_ENABLED, false))
    private val scheduleStartFlow = MutableStateFlow(store.getInt(KEY_SCHEDULE_START_MINUTE, DEFAULT_SCHEDULE_START))
    private val scheduleEndFlow = MutableStateFlow(store.getInt(KEY_SCHEDULE_END_MINUTE, DEFAULT_SCHEDULE_END))
    private val themeModeFlow = MutableStateFlow(ThemeMode.fromStorage(store.getString(KEY_THEME_MODE)))
    private val dynamicColorFlow = MutableStateFlow(store.getBoolean(KEY_DYNAMIC_COLOR, false))
    private val timerFireAtFlow = MutableStateFlow(store.getLong(KEY_TIMER_FIRE_AT, NONE))
    private val updateCheckEnabledFlow = MutableStateFlow(store.getBoolean(KEY_UPDATE_CHECK_ENABLED, false))
    private val appliedSnapshotFlow = MutableStateFlow(AppliedSnapshot.fromJson(store.getString(KEY_APPLIED_SNAPSHOT)))
    private val sessionsFlow = MutableStateFlow(loadSessions())
    private val logEntriesFlow = MutableStateFlow<List<CommandLogEntry>>(emptyList())
    private val sessionsLock = Any()

    val isOn: StateFlow<Boolean> = isOnFlow.asStateFlow()
    val isOnTimestampMs: StateFlow<Long> = isOnTimestampFlow.asStateFlow()
    val simSlotMode: StateFlow<SimSlotMode> = simSlotModeFlow.asStateFlow()
    val activePresetId: StateFlow<String> = activePresetIdFlow.asStateFlow()
    val notificationEnabled: StateFlow<Boolean> = notificationEnabledFlow.asStateFlow()
    val scheduleEnabled: StateFlow<Boolean> = scheduleEnabledFlow.asStateFlow()
    val scheduleStartMinuteOfDay: StateFlow<Int> = scheduleStartFlow.asStateFlow()
    val scheduleEndMinuteOfDay: StateFlow<Int> = scheduleEndFlow.asStateFlow()
    val themeMode: StateFlow<ThemeMode> = themeModeFlow.asStateFlow()
    val dynamicColor: StateFlow<Boolean> = dynamicColorFlow.asStateFlow()
    val timerFireAtMs: StateFlow<Long> = timerFireAtFlow.asStateFlow()
    val updateCheckEnabled: StateFlow<Boolean> = updateCheckEnabledFlow.asStateFlow()
    val appliedSnapshot: StateFlow<AppliedSnapshot?> = appliedSnapshotFlow.asStateFlow()
    val sessions: StateFlow<List<GhostSession>> = sessionsFlow.asStateFlow()
    val logEntries: StateFlow<List<CommandLogEntry>> = logEntriesFlow.asStateFlow()

    // --- Mode state -------------------------------------------------------------------------

    /**
     * Marks the mode as applied. [snapshot] describes how to undo it; the timestamp of an
     * already running session is preserved when the state is merely re-applied.
     */
    fun markOn(snapshot: AppliedSnapshot) {
        val wasOn = isOnFlow.value
        val timestampMs = if (wasOn && isOnTimestampFlow.value != NONE) isOnTimestampFlow.value else clock()
        isOnFlow.value = true
        isOnTimestampFlow.value = timestampMs
        appliedSnapshotFlow.value = snapshot
        store.edit {
            putBoolean(KEY_IS_ON, true)
            putLong(KEY_IS_ON_TIMESTAMP, timestampMs)
            putString(KEY_APPLIED_SNAPSHOT, snapshot.toJson())
        }
        if (!wasOn) openSession(timestampMs)
    }

    fun markOff() {
        isOnFlow.value = false
        isOnTimestampFlow.value = NONE
        appliedSnapshotFlow.value = null
        timerFireAtFlow.value = NONE
        store.edit {
            putBoolean(KEY_IS_ON, false)
            putLong(KEY_IS_ON_TIMESTAMP, NONE)
            remove(KEY_APPLIED_SNAPSHOT)
            putLong(KEY_TIMER_FIRE_AT, NONE)
        }
        closeOpenSessions()
    }

    fun getSavedNetworkMask(slot: Int): String? =
        if (slot == 1) savedMaskSlot1Flow.value else savedMaskSlot0Flow.value

    fun setSavedNetworkMask(slot: Int, mask: String?) {
        if (slot == 1) {
            savedMaskSlot1Flow.value = mask
            store.edit { putString(KEY_SAVED_MASK_SLOT_1, mask) }
        } else {
            savedMaskSlot0Flow.value = mask
            store.edit {
                putString(KEY_SAVED_MASK, mask)
                putLong(KEY_SAVED_MASK_TS, if (mask == null) NONE else clock())
            }
        }
    }

    // --- Preferences ------------------------------------------------------------------------

    fun setSimSlotMode(mode: SimSlotMode) {
        simSlotModeFlow.value = mode
        store.edit { putString(KEY_SIM_SLOT_MODE, mode.name) }
    }

    fun setActivePresetId(presetId: String) {
        activePresetIdFlow.value = presetId
        store.edit { putString(KEY_ACTIVE_PRESET_ID, presetId) }
    }

    fun setNotificationEnabled(value: Boolean) {
        notificationEnabledFlow.value = value
        store.edit { putBoolean(KEY_NOTIFICATION_ENABLED, value) }
    }

    fun setSchedule(enabled: Boolean, startMinuteOfDay: Int, endMinuteOfDay: Int) {
        scheduleEnabledFlow.value = enabled
        scheduleStartFlow.value = startMinuteOfDay
        scheduleEndFlow.value = endMinuteOfDay
        store.edit {
            putBoolean(KEY_SCHEDULE_ENABLED, enabled)
            putInt(KEY_SCHEDULE_START_MINUTE, startMinuteOfDay)
            putInt(KEY_SCHEDULE_END_MINUTE, endMinuteOfDay)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        themeModeFlow.value = mode
        store.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    fun setDynamicColor(enabled: Boolean) {
        dynamicColorFlow.value = enabled
        store.edit { putBoolean(KEY_DYNAMIC_COLOR, enabled) }
    }

    fun setUpdateCheckEnabled(enabled: Boolean) {
        updateCheckEnabledFlow.value = enabled
        store.edit { putBoolean(KEY_UPDATE_CHECK_ENABLED, enabled) }
    }

    fun setTimerFireAtMs(valueMs: Long) {
        timerFireAtFlow.value = valueMs
        store.edit { putLong(KEY_TIMER_FIRE_AT, valueMs) }
    }

    fun clearTimer() = setTimerFireAtMs(NONE)

    // --- Command log (in memory only) -------------------------------------------------------

    fun appendLog(entry: CommandLogEntry) {
        logEntriesFlow.update { current -> (current + entry).takeLast(LOG_CAPACITY) }
    }

    fun removeLogEntry(entry: CommandLogEntry) {
        logEntriesFlow.update { current -> current - entry }
    }

    fun clearLog() {
        logEntriesFlow.value = emptyList()
    }

    // --- Sessions ---------------------------------------------------------------------------

    private fun openSession(startMs: Long) {
        if (sessionsFlow.value.any { it.isOpen }) return
        updateSessions { it + GhostSession(startMs, GhostSession.SESSION_END_OPEN) }
    }

    private fun closeOpenSessions() {
        val nowMs = clock()
        updateSessions { sessions ->
            sessions.map { session -> if (session.isOpen) session.copy(endMs = nowMs) else session }
        }
    }

    private fun updateSessions(transform: (List<GhostSession>) -> List<GhostSession>) {
        synchronized(sessionsLock) {
            val updated = transform(sessionsFlow.value).takeLast(SESSION_CAPACITY)
            sessionsFlow.value = updated
            store.edit { putString(KEY_SESSIONS, sessionsToJson(updated)) }
        }
    }

    private fun loadSessions(): List<GhostSession> {
        val json = store.getString(KEY_SESSIONS) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                GhostSession(item.getLong(KEY_START_MS), item.getLong(KEY_END_MS))
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun sessionsToJson(sessions: List<GhostSession>): String {
        val array = JSONArray()
        sessions.forEach { session ->
            array.put(JSONObject().put(KEY_START_MS, session.startMs).put(KEY_END_MS, session.endMs))
        }
        return array.toString()
    }

    companion object {
        const val LOG_CAPACITY = 300
        const val PREFS_NAME = "ghost_state"
        const val NONE = 0L

        private const val KEY_IS_ON = "is_on"
        private const val KEY_IS_ON_TIMESTAMP = "is_on_timestamp"
        private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        private const val KEY_SAVED_MASK = "saved_mask"
        private const val KEY_SAVED_MASK_SLOT_1 = "saved_mask_slot_1"
        private const val KEY_SAVED_MASK_TS = "saved_mask_ts"
        private const val KEY_SIM_SLOT_MODE = "sim_slot_mode"
        private const val KEY_ACTIVE_PRESET_ID = "active_preset_id"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_START_MS = "startMs"
        private const val KEY_END_MS = "endMs"
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        private const val KEY_SCHEDULE_START_MINUTE = "schedule_start_minute"
        private const val KEY_SCHEDULE_END_MINUTE = "schedule_end_minute"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_TIMER_FIRE_AT = "timer_fire_at"
        private const val KEY_UPDATE_CHECK_ENABLED = "update_check_enabled"
        private const val KEY_APPLIED_SNAPSHOT = "applied_snapshot"

        private const val SESSION_CAPACITY = 500
        private const val DEFAULT_SCHEDULE_START = 23 * 60
        private const val DEFAULT_SCHEDULE_END = 8 * 60
    }
}
