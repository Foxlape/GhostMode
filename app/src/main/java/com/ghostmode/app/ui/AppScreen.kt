package com.ghostmode.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ghostmode.app.R
import com.ghostmode.app.data.CommandLogEntry
import com.ghostmode.app.data.GhostSession
import com.ghostmode.app.data.Preset
import com.ghostmode.app.shell.ShizukuStatus
import com.ghostmode.app.support.DONATE_URL
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

private const val DUPLICATE_TITLE_SUFFIX = " (копия)"
private const val MASK_VALUE_SEPARATOR = " "
private const val BLANK_PRESET_ID = ""
private const val BLANK_TEXT = ""
private const val LANGUAGE_TAG_SYSTEM = "system"
private const val LANGUAGE_TAG_RU = "ru"
private const val LANGUAGE_TAG_EN = "en"
private const val WEIGHT_FILL = 1f
private const val FIXED_SECTION_COUNT = 7
private const val SESSION_END_OPEN = 0L
private const val DURATION_FLOOR_MS = 0L
private const val MINUTE_DURATION_MS = 60_000L
private const val MINUTES_PER_HOUR = 60
private const val DAY_HOURS = 24
private const val WEEK_DAYS = 7
private const val WEEK_DURATION_MS = WEEK_DAYS * DAY_HOURS * MINUTES_PER_HOUR * MINUTE_DURATION_MS
private const val DAY_START_HOUR = 0
private const val DAY_START_MINUTE = 0
private const val TIME_LABEL_SEPARATOR = " "
private const val TIME_LABEL_FORMAT = "%02d:%02d"
private const val SCHEDULE_TARGET_NONE = 0
private const val SCHEDULE_TARGET_START = 1
private const val SCHEDULE_TARGET_END = 2
private const val IS_24_HOUR_FORMAT = true
private val MAX_CONTENT_WIDTH = 640.dp
private val SCREEN_PADDING = 16.dp
private val SECTION_SPACING = 16.dp
private val CARD_PADDING = 16.dp
private val CARD_ITEM_SPACING = 8.dp

@Composable
fun AppScreen(
    shizukuStatus: ShizukuStatus,
    rootAvailable: Boolean,
    isGhostModeOn: Boolean,
    isBusy: Boolean,
    presets: List<Preset>,
    activePresetId: String,
    savedNetworkMask: String?,
    logEntries: List<CommandLogEntry>,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSelectPreset: (String) -> Unit,
    onSavePreset: (Preset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onRunDiagnostics: () -> Unit,
    onClearLog: () -> Unit,
    onRemoveEntry: (CommandLogEntry) -> Unit,
    notificationEnabled: Boolean,
    onNotificationEnabled: (Boolean) -> Unit,
    onLanguageSelected: (String) -> Unit,
    sessions: List<GhostSession>,
    scheduleEnabled: Boolean,
    scheduleStartMinute: Int,
    scheduleEndMinute: Int,
    onScheduleEnabled: (Boolean) -> Unit,
    onScheduleStart: (Int) -> Unit,
    onScheduleEnd: (Int) -> Unit,
    onExportPresets: () -> Unit,
    onImportPresets: () -> Unit
) {
    var editorPreset by remember { mutableStateOf<Preset?>(null) }
    var editorIsNewPreset by remember { mutableStateOf(false) }
    val isActionEnabled = !isBusy && (rootAvailable || shizukuStatus == ShizukuStatus.READY)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val logSectionIndex = presets.size + FIXED_SECTION_COUNT - 1
    val screenInsets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = MAX_CONTENT_WIDTH)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = screenInsets.calculateStartPadding(layoutDirection) + SCREEN_PADDING,
                    top = screenInsets.calculateTopPadding() + SCREEN_PADDING,
                    end = screenInsets.calculateEndPadding(layoutDirection) + SCREEN_PADDING,
                    bottom = screenInsets.calculateBottomPadding() + SCREEN_PADDING
                ),
                verticalArrangement = Arrangement.spacedBy(SECTION_SPACING)
            ) {
                item {
                    GhostModeCard(
                        isGhostModeOn = isGhostModeOn,
                        isToggleEnabled = isActionEnabled,
                        isBusy = isBusy,
                        onToggle = onToggle
                    )
                }
                item {
                    if (rootAvailable) {
                        RootStatusCard()
                    } else {
                        ShizukuStatusCard(shizukuStatus, onRequestPermission, onOpenShizuku)
                    }
                }
                item {
                    PresetsSectionHeader(
                        onCreatePreset = {
                            editorPreset = blankPresetDraft()
                            editorIsNewPreset = true
                        },
                        onExportPresets = onExportPresets,
                        onImportPresets = onImportPresets
                    )
                }
                items(presets, key = { preset -> preset.id }) { preset ->
                    PresetCard(
                        preset = preset,
                        isSelected = preset.id == activePresetId,
                        onSelect = { onSelectPreset(preset.id) },
                        onDuplicate = {
                            editorPreset = preset.duplicateDraft()
                            editorIsNewPreset = true
                        },
                        onEdit = {
                            editorPreset = preset
                            editorIsNewPreset = false
                        },
                        onDelete = { onDeletePreset(preset.id) }
                    )
                }
                item {
                    DiagnosticsCard(
                        isRunEnabled = isActionEnabled,
                        savedNetworkMask = savedNetworkMask,
                        onRunDiagnostics = {
                            onRunDiagnostics()
                            scope.launch { listState.animateScrollToItem(logSectionIndex) }
                        }
                    )
                }
                item {
                    StatsCard(sessions = sessions)
                }
                item {
                    SettingsCard(
                        notificationEnabled = notificationEnabled,
                        onNotificationEnabled = onNotificationEnabled,
                        onLanguageSelected = onLanguageSelected,
                        scheduleEnabled = scheduleEnabled,
                        scheduleStartMinute = scheduleStartMinute,
                        scheduleEndMinute = scheduleEndMinute,
                        onScheduleEnabled = onScheduleEnabled,
                        onScheduleStart = onScheduleStart,
                        onScheduleEnd = onScheduleEnd
                    )
                }
                item {
                    LogPanel(
                        entries = logEntries,
                        onClear = onClearLog,
                        onRemoveEntry = onRemoveEntry
                    )
                }
            }
        }
    }

    editorPreset?.let { preset ->
        PresetEditorDialog(
            initialPreset = preset,
            isNewPreset = editorIsNewPreset,
            onSave = { editedPreset ->
                onSavePreset(editedPreset)
                editorPreset = null
            },
            onDismiss = { editorPreset = null }
        )
    }
}

@Composable
private fun RootStatusCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(CARD_ITEM_SPACING)
        ) {
            Text(
                text = stringResource(R.string.root_status_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.root_status_ready),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShizukuStatusCard(
    shizukuStatus: ShizukuStatus,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(CARD_ITEM_SPACING)
        ) {
            Text(
                text = stringResource(R.string.shizuku_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(shizukuStatusLabelRes(shizukuStatus)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (shizukuStatus) {
                ShizukuStatus.NO_PERMISSION -> TextButton(onClick = onRequestPermission) {
                    Text(text = stringResource(R.string.action_grant_permission))
                }
                ShizukuStatus.NOT_RUNNING, ShizukuStatus.NOT_INSTALLED ->
                    TextButton(onClick = onOpenShizuku) {
                        Text(text = stringResource(R.string.action_open_shizuku))
                    }
                ShizukuStatus.READY -> Unit
            }
        }
    }
}

private fun shizukuStatusLabelRes(status: ShizukuStatus): Int = when (status) {
    ShizukuStatus.READY -> R.string.shizuku_status_ready
    ShizukuStatus.NO_PERMISSION -> R.string.shizuku_status_no_permission
    ShizukuStatus.NOT_RUNNING -> R.string.shizuku_status_not_running
    ShizukuStatus.NOT_INSTALLED -> R.string.shizuku_status_not_installed
}

@Composable
private fun GhostModeCard(
    isGhostModeOn: Boolean,
    isToggleEnabled: Boolean,
    isBusy: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(CARD_ITEM_SPACING)
        ) {
            Text(
                text = stringResource(
                    if (isGhostModeOn) R.string.status_title_on else R.string.status_title_off
                ),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(
                    if (isGhostModeOn) R.string.status_subtitle_on else R.string.status_subtitle_off
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CARD_ITEM_SPACING)
            ) {
                Switch(
                    checked = isGhostModeOn,
                    onCheckedChange = onToggle,
                    enabled = isToggleEnabled
                )
                Text(
                    text = stringResource(
                        if (isGhostModeOn) R.string.toggle_turn_off else R.string.toggle_turn_on
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.busy_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PresetsSectionHeader(
    onCreatePreset: () -> Unit,
    onExportPresets: () -> Unit,
    onImportPresets: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.presets_title),
            style = MaterialTheme.typography.titleMedium
        )
        Row {
            TextButton(onClick = onExportPresets) {
                Text(text = stringResource(R.string.presets_action_export))
            }
            TextButton(onClick = onImportPresets) {
                Text(text = stringResource(R.string.presets_action_import))
            }
            TextButton(onClick = onCreatePreset) {
                Text(text = stringResource(R.string.editor_title_new))
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: Preset,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(CARD_ITEM_SPACING)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(WEIGHT_FILL)
                )
                AssistChip(
                    onClick = onSelect,
                    label = {
                        Text(
                            text = stringResource(
                                if (preset.isBuiltIn) R.string.preset_builtin
                                else R.string.preset_custom
                            )
                        )
                    }
                )
            }
            if (preset.description.isNotBlank()) {
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                TextButton(onClick = onDuplicate) {
                    Text(text = stringResource(R.string.action_duplicate))
                }
                if (!preset.isBuiltIn) {
                    TextButton(onClick = onEdit) {
                        Text(text = stringResource(R.string.action_edit))
                    }
                    TextButton(onClick = onDelete) {
                        Text(text = stringResource(R.string.action_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(
    isRunEnabled: Boolean,
    savedNetworkMask: String?,
    onRunDiagnostics: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(CARD_ITEM_SPACING)
        ) {
            Text(
                text = stringResource(R.string.diagnostics_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.diagnostics_mask_label) +
                    MASK_VALUE_SEPARATOR +
                    (savedNetworkMask ?: stringResource(R.string.diagnostics_mask_none)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRunDiagnostics, enabled = isRunEnabled) {
                Text(text = stringResource(R.string.action_run_diagnostics))
            }
        }
    }
}

@Composable
private fun StatsCard(sessions: List<GhostSession>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(CARD_ITEM_SPACING)
        ) {
            Text(
                text = stringResource(R.string.stats_title),
                style = MaterialTheme.typography.titleMedium
            )
            StatsRow(
                label = stringResource(R.string.stats_today),
                value = durationLabel(todayDurationMs(sessions))
            )
            StatsRow(
                label = stringResource(R.string.stats_week),
                value = durationLabel(weekDurationMs(sessions))
            )
            StatsRow(
                label = stringResource(R.string.stats_all),
                value = durationLabel(totalDurationMs(sessions))
            )
            Text(
                text = stringResource(R.string.stats_activations, sessions.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun todayDurationMs(sessions: List<GhostSession>): Long {
    val nowMs = System.currentTimeMillis()
    return sessionsDurationBetween(sessions, startOfTodayMs(), nowMs)
}

private fun weekDurationMs(sessions: List<GhostSession>): Long {
    val nowMs = System.currentTimeMillis()
    return sessionsDurationBetween(sessions, nowMs - WEEK_DURATION_MS, nowMs)
}

private fun totalDurationMs(sessions: List<GhostSession>): Long {
    val nowMs = System.currentTimeMillis()
    return sessions.sumOf { session ->
        val sessionEndMs = if (session.endMs == SESSION_END_OPEN) nowMs else session.endMs
        (sessionEndMs - session.startMs).coerceAtLeast(DURATION_FLOOR_MS)
    }
}

private fun sessionsDurationBetween(
    sessions: List<GhostSession>,
    fromMs: Long,
    toMs: Long
): Long = sessions.sumOf { session ->
    val sessionEndMs = if (session.endMs == SESSION_END_OPEN) toMs else session.endMs
    val overlapStartMs = maxOf(session.startMs, fromMs)
    val overlapEndMs = minOf(sessionEndMs, toMs)
    (overlapEndMs - overlapStartMs).coerceAtLeast(DURATION_FLOOR_MS)
}

private fun startOfTodayMs(): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, DAY_START_HOUR)
    calendar.set(Calendar.MINUTE, DAY_START_MINUTE)
    calendar.set(Calendar.SECOND, DAY_START_MINUTE)
    calendar.set(Calendar.MILLISECOND, DAY_START_MINUTE)
    return calendar.timeInMillis
}

@Composable
private fun durationLabel(durationMs: Long): String {
    val totalMinutes = durationMs / MINUTE_DURATION_MS
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours == 0L) {
        stringResource(R.string.stats_duration_minutes, minutes)
    } else {
        stringResource(R.string.stats_duration_hours_minutes, hours, minutes)
    }
}

@Composable
private fun SettingsCard(
    notificationEnabled: Boolean,
    onNotificationEnabled: (Boolean) -> Unit,
    onLanguageSelected: (String) -> Unit,
    scheduleEnabled: Boolean,
    scheduleStartMinute: Int,
    scheduleEndMinute: Int,
    onScheduleEnabled: (Boolean) -> Unit,
    onScheduleStart: (Int) -> Unit,
    onScheduleEnd: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(CARD_ITEM_SPACING)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium
            )
            LanguageSelector(onLanguageSelected = onLanguageSelected)
            NotificationToggle(
                checked = notificationEnabled,
                onCheckedChange = onNotificationEnabled
            )
            ScheduleSection(
                enabled = scheduleEnabled,
                startMinuteOfDay = scheduleStartMinute,
                endMinuteOfDay = scheduleEndMinute,
                onEnabled = onScheduleEnabled,
                onStart = onScheduleStart,
                onEnd = onScheduleEnd
            )
            if (DONATE_URL.isNotBlank()) {
                DonateButton()
            }
        }
    }
}

@Composable
private fun LanguageSelector(onLanguageSelected: (String) -> Unit) {
    val selectedTag = currentApplicationLanguageTag()
    Column(verticalArrangement = Arrangement.spacedBy(CARD_ITEM_SPACING)) {
        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.titleSmall
        )
        LanguageOption(
            label = stringResource(R.string.language_system),
            tag = LANGUAGE_TAG_SYSTEM,
            selectedTag = selectedTag,
            onLanguageSelected = onLanguageSelected
        )
        LanguageOption(
            label = stringResource(R.string.language_russian),
            tag = LANGUAGE_TAG_RU,
            selectedTag = selectedTag,
            onLanguageSelected = onLanguageSelected
        )
        LanguageOption(
            label = stringResource(R.string.language_english),
            tag = LANGUAGE_TAG_EN,
            selectedTag = selectedTag,
            onLanguageSelected = onLanguageSelected
        )
    }
}

@Composable
private fun LanguageOption(
    label: String,
    tag: String,
    selectedTag: String,
    onLanguageSelected: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = tag == selectedTag,
            onClick = { onLanguageSelected(tag) }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = CARD_ITEM_SPACING)
        )
    }
}

@Composable
private fun NotificationToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onCheckedChange(true) }
    Column(verticalArrangement = Arrangement.spacedBy(CARD_ITEM_SPACING)) {
        Text(
            text = stringResource(R.string.settings_notification),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(R.string.settings_notification_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Switch(
            checked = checked,
            onCheckedChange = { enabled ->
                if (enabled && isNotificationPermissionMissing(context)) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onCheckedChange(enabled)
                }
            }
        )
    }
}

@Composable
private fun ScheduleSection(
    enabled: Boolean,
    startMinuteOfDay: Int,
    endMinuteOfDay: Int,
    onEnabled: (Boolean) -> Unit,
    onStart: (Int) -> Unit,
    onEnd: (Int) -> Unit
) {
    var pickerTarget by remember { mutableStateOf(SCHEDULE_TARGET_NONE) }
    Column(verticalArrangement = Arrangement.spacedBy(CARD_ITEM_SPACING)) {
        Text(
            text = stringResource(R.string.schedule_title),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(R.string.schedule_enabled),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(checked = enabled, onCheckedChange = onEnabled)
        Row(horizontalArrangement = Arrangement.spacedBy(CARD_ITEM_SPACING)) {
            OutlinedButton(onClick = { pickerTarget = SCHEDULE_TARGET_START }) {
                Text(text = scheduleTimeLabel(R.string.schedule_from, startMinuteOfDay))
            }
            OutlinedButton(onClick = { pickerTarget = SCHEDULE_TARGET_END }) {
                Text(text = scheduleTimeLabel(R.string.schedule_to, endMinuteOfDay))
            }
        }
        Text(
            text = stringResource(R.string.schedule_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (pickerTarget != SCHEDULE_TARGET_NONE) {
        ScheduleTimeDialog(
            initialMinuteOfDay = if (pickerTarget == SCHEDULE_TARGET_START) startMinuteOfDay else endMinuteOfDay,
            onConfirm = { minuteOfDay ->
                if (pickerTarget == SCHEDULE_TARGET_START) onStart(minuteOfDay) else onEnd(minuteOfDay)
                pickerTarget = SCHEDULE_TARGET_NONE
            },
            onDismiss = { pickerTarget = SCHEDULE_TARGET_NONE }
        )
    }
}

@Composable
private fun scheduleTimeLabel(labelRes: Int, minuteOfDay: Int): String =
    stringResource(labelRes) + TIME_LABEL_SEPARATOR + String.format(
        Locale.ROOT,
        TIME_LABEL_FORMAT,
        minuteOfDay / MINUTES_PER_HOUR,
        minuteOfDay % MINUTES_PER_HOUR
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTimeDialog(
    initialMinuteOfDay: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialMinuteOfDay / MINUTES_PER_HOUR,
        initialMinute = initialMinuteOfDay % MINUTES_PER_HOUR,
        is24Hour = IS_24_HOUR_FORMAT
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(timePickerState.hour * MINUTES_PER_HOUR + timePickerState.minute)
                }
            ) {
                Text(text = stringResource(R.string.schedule_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.schedule_cancel))
            }
        },
        text = { TimePicker(state = timePickerState) }
    )
}

@Composable
private fun DonateButton() {
    val context = LocalContext.current
    ElevatedButton(
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DONATE_URL)))
            } catch (_: ActivityNotFoundException) {
            }
        }
    ) {
        Text(text = stringResource(R.string.settings_donate_title))
    }
}

private fun currentApplicationLanguageTag(): String {
    val applicationLocales = AppCompatDelegate.getApplicationLocales()
    return when {
        applicationLocales.isEmpty -> LANGUAGE_TAG_SYSTEM
        applicationLocales.toLanguageTags().startsWith(LANGUAGE_TAG_RU) -> LANGUAGE_TAG_RU
        else -> LANGUAGE_TAG_EN
    }
}

private fun isNotificationPermissionMissing(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

private fun blankPresetDraft(): Preset = Preset(
    id = BLANK_PRESET_ID,
    title = BLANK_TEXT,
    description = BLANK_TEXT,
    onCommands = emptyList(),
    offCommands = emptyList(),
    networkMaskCaptureCommand = null,
    isBuiltIn = false
)

private fun Preset.duplicateDraft(): Preset = copy(
    id = BLANK_PRESET_ID,
    title = title + DUPLICATE_TITLE_SUFFIX,
    isBuiltIn = false
)
