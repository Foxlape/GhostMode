package com.ghostmode.app.data

import android.content.Context
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
)

data class GhostSession(
    val startMs: Long,
    val endMs: Long
)

open class GhostStateRepository(private val context: Context? = null) {

    private val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val isOnFlow = MutableStateFlow(prefs?.getBoolean(KEY_IS_ON, false) ?: false)
    private val isOnTimestampMsFlow = MutableStateFlow(prefs?.getLong(KEY_IS_ON_TIMESTAMP, TIMESTAMP_NONE) ?: TIMESTAMP_NONE)
    private val savedNetworkMaskFlow = MutableStateFlow(prefs?.getString(KEY_SAVED_MASK, null))
    private val savedMaskTimestampMsFlow = MutableStateFlow(prefs?.getLong(KEY_SAVED_MASK_TS, TIMESTAMP_NONE) ?: TIMESTAMP_NONE)
    private val activePresetIdFlow = MutableStateFlow(
        prefs?.getString(KEY_ACTIVE_PRESET_ID, null) ?: BuiltInPresets.DEFAULT_ID
    )
    private val logEntriesFlow = MutableStateFlow<List<CommandLogEntry>>(EMPTY_LOG)
    private val notificationEnabledFlow = MutableStateFlow(prefs?.getBoolean(KEY_NOTIFICATION_ENABLED, false) ?: false)
    private val sessionsFlow = MutableStateFlow(loadSessions())
    private val scheduleEnabledFlow = MutableStateFlow(prefs?.getBoolean(KEY_SCHEDULE_ENABLED, SCHEDULE_DEFAULT_DISABLED) ?: SCHEDULE_DEFAULT_DISABLED)
    private val scheduleStartMinuteOfDayFlow = MutableStateFlow(
        prefs?.getInt(KEY_SCHEDULE_START_MINUTE, DEFAULT_SCHEDULE_START_MINUTE) ?: DEFAULT_SCHEDULE_START_MINUTE
    )
    private val scheduleEndMinuteOfDayFlow = MutableStateFlow(
        prefs?.getInt(KEY_SCHEDULE_END_MINUTE, DEFAULT_SCHEDULE_END_MINUTE) ?: DEFAULT_SCHEDULE_END_MINUTE
    )

    open val isOn: StateFlow<Boolean> = isOnFlow.asStateFlow()
    open val isOnTimestampMs: StateFlow<Long> = isOnTimestampMsFlow.asStateFlow()
    open val savedNetworkMask: StateFlow<String?> = savedNetworkMaskFlow.asStateFlow()
    open val savedMaskTimestampMs: StateFlow<Long> = savedMaskTimestampMsFlow.asStateFlow()
    open val activePresetId: StateFlow<String> = activePresetIdFlow.asStateFlow()
    open val logEntries: StateFlow<List<CommandLogEntry>> = logEntriesFlow.asStateFlow()
    open val notificationEnabled: StateFlow<Boolean> = notificationEnabledFlow.asStateFlow()
    open val sessions: StateFlow<List<GhostSession>> = sessionsFlow.asStateFlow()
    open val scheduleEnabled: StateFlow<Boolean> = scheduleEnabledFlow.asStateFlow()
    open val scheduleStartMinuteOfDay: StateFlow<Int> = scheduleStartMinuteOfDayFlow.asStateFlow()
    open val scheduleEndMinuteOfDay: StateFlow<Int> = scheduleEndMinuteOfDayFlow.asStateFlow()

    open fun setIsOn(value: Boolean) {
        val timestampMs = if (value) System.currentTimeMillis() else TIMESTAMP_NONE
        isOnFlow.value = value
        isOnTimestampMsFlow.value = timestampMs
        if (value) openSession() else closeOpenSessions()
        prefs?.edit()
            ?.putBoolean(KEY_IS_ON, value)
            ?.putLong(KEY_IS_ON_TIMESTAMP, timestampMs)
            ?.apply()
        context?.let { ctx ->
            com.ghostmode.app.widget.GhostWidgetProvider.refreshAll(ctx, value)
            com.ghostmode.app.tile.GhostTileService.requestTileUpdate(ctx)
            com.ghostmode.app.service.StatusNotificationManager.update(
                ctx,
                value,
                notificationEnabledFlow.value,
                timestampMs
            )
        }
    }

    open fun setNotificationEnabled(value: Boolean) {
        notificationEnabledFlow.value = value
        prefs?.edit()?.putBoolean(KEY_NOTIFICATION_ENABLED, value)?.apply()
        context?.let { ctx ->
            com.ghostmode.app.service.StatusNotificationManager.update(
                ctx,
                isOnFlow.value,
                value,
                isOnTimestampMsFlow.value
            )
        }
    }

    open fun setScheduleEnabled(value: Boolean) {
        scheduleEnabledFlow.value = value
        prefs?.edit()?.putBoolean(KEY_SCHEDULE_ENABLED, value)?.apply()
    }

    open fun setScheduleStartMinuteOfDay(minuteOfDay: Int) {
        scheduleStartMinuteOfDayFlow.value = minuteOfDay
        prefs?.edit()?.putInt(KEY_SCHEDULE_START_MINUTE, minuteOfDay)?.apply()
    }

    open fun setScheduleEndMinuteOfDay(minuteOfDay: Int) {
        scheduleEndMinuteOfDayFlow.value = minuteOfDay
        prefs?.edit()?.putInt(KEY_SCHEDULE_END_MINUTE, minuteOfDay)?.apply()
    }

    private fun openSession() {
        val hasOpenSession = sessionsFlow.value.any { session -> session.endMs == SESSION_END_OPEN }
        if (hasOpenSession) return
        updateSessions { sessions -> sessions + GhostSession(System.currentTimeMillis(), SESSION_END_OPEN) }
    }

    private fun closeOpenSessions() {
        val nowMs = System.currentTimeMillis()
        updateSessions { sessions ->
            sessions.map { session ->
                if (session.endMs == SESSION_END_OPEN) session.copy(endMs = nowMs) else session
            }
        }
    }

    private fun updateSessions(transform: (List<GhostSession>) -> List<GhostSession>) {
        val updatedSessions = transform(sessionsFlow.value).takeLast(SESSION_CAPACITY)
        sessionsFlow.value = updatedSessions
        prefs?.edit()?.putString(KEY_SESSIONS, sessionsToJson(updatedSessions))?.apply()
    }

    private fun loadSessions(): List<GhostSession> {
        val storedJson = prefs?.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            sessionsFromJson(storedJson)
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun sessionsFromJson(storedJson: String): List<GhostSession> {
        val jsonArray = JSONArray(storedJson)
        return buildList {
            for (index in 0 until jsonArray.length()) {
                val sessionJson = jsonArray.getJSONObject(index)
                add(GhostSession(sessionJson.getLong(KEY_START_MS), sessionJson.getLong(KEY_END_MS)))
            }
        }
    }

    private fun sessionsToJson(sessions: List<GhostSession>): String {
        val jsonArray = JSONArray()
        sessions.forEach { session ->
            jsonArray.put(
                JSONObject()
                    .put(KEY_START_MS, session.startMs)
                    .put(KEY_END_MS, session.endMs)
            )
        }
        return jsonArray.toString()
    }

    open fun setSavedNetworkMask(mask: String?) {
        val timestampMs = if (mask == null) TIMESTAMP_NONE else System.currentTimeMillis()
        savedNetworkMaskFlow.value = mask
        savedMaskTimestampMsFlow.value = timestampMs
        prefs?.edit()
            ?.putString(KEY_SAVED_MASK, mask)
            ?.putLong(KEY_SAVED_MASK_TS, timestampMs)
            ?.apply()
    }

    open fun setActivePresetId(presetId: String) {
        activePresetIdFlow.value = presetId
        prefs?.edit()?.putString(KEY_ACTIVE_PRESET_ID, presetId)?.apply()
    }

    open fun appendLog(entry: CommandLogEntry) {
        logEntriesFlow.update { current -> (current + entry).takeLast(LOG_CAPACITY) }
    }

    open fun removeLogEntry(timestampMs: Long) {
        logEntriesFlow.update { current ->
            current.filterNot { entry -> entry.timestampMs == timestampMs }
        }
    }

    open fun clearLog() {
        logEntriesFlow.value = EMPTY_LOG
    }

    companion object {
        const val LOG_CAPACITY = 200
        private const val PREFS_NAME = "ghost_state"
        private const val KEY_IS_ON = "is_on"
        private const val KEY_IS_ON_TIMESTAMP = "is_on_timestamp"
        private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        private const val KEY_SAVED_MASK = "saved_mask"
        private const val KEY_SAVED_MASK_TS = "saved_mask_ts"
        private const val KEY_ACTIVE_PRESET_ID = "active_preset_id"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_START_MS = "startMs"
        private const val KEY_END_MS = "endMs"
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        private const val KEY_SCHEDULE_START_MINUTE = "schedule_start_minute"
        private const val KEY_SCHEDULE_END_MINUTE = "schedule_end_minute"
        private const val TIMESTAMP_NONE = 0L
        private const val SESSION_END_OPEN = 0L
        private const val SESSION_CAPACITY = 500
        private const val SCHEDULE_DEFAULT_DISABLED = false
        private const val DEFAULT_SCHEDULE_START_MINUTE = 1380
        private const val DEFAULT_SCHEDULE_END_MINUTE = 480
        private val EMPTY_LOG: List<CommandLogEntry> = emptyList()
    }
}
