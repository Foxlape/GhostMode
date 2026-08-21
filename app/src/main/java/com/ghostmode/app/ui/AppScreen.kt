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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.ghostmode.app.R
import com.ghostmode.app.data.CommandLogEntry
import com.ghostmode.app.data.GhostSession
import com.ghostmode.app.data.Preset
import com.ghostmode.app.data.SimSlotMode
import com.ghostmode.app.data.ThemeMode
import com.ghostmode.app.shell.ShizukuStatus
import com.ghostmode.app.support.DONATE_URL
import com.ghostmode.app.support.GitHubRelease
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

private val HORIZONTAL_PADDING = 16.dp
private val VERTICAL_PADDING = 16.dp
private val SECTION_SPACING = 16.dp
private const val RELEASE_CERT_SHA256 = "FB:2A:E9:C4:80:BB:0F:04:55:65:F7:B5:CA:BF:01:7D:98:18:21:A9:33:F0:78:53:DD:47:12:28:D5:71:B0:50"
private const val TIMER_TICK_INTERVAL_MS = 30_000L
private const val TIMER_CHIP_30M_MINUTES = 30
private const val TIMER_CHIP_1H_MINUTES = 60
private const val TIMER_CHIP_2H_MINUTES = 120

private enum class ActiveDialog {
    NONE,
    DIAGNOSTICS,
    LOGS,
    SCHEDULE,
    LANGUAGE,
    THEME,
    UPDATES,
    STATS,
    ABOUT,
    DONATE,
    PRESET_EDITOR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    isOn: Boolean,
    onToggle: () -> Unit,
    isBusy: Boolean,
    shizukuStatus: ShizukuStatus,
    isRootAvailable: Boolean,
    onGrantPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onDownloadShizuku: () -> Unit,
    presets: List<Preset>,
    activePresetId: String,
    onSelectPreset: (String) -> Unit,
    onSavePreset: (Preset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onDuplicatePreset: (Preset) -> Unit,
    onExportPresets: (Uri) -> Unit,
    onImportPresets: (Uri) -> Unit,
    savedNetworkMask: String?,
    onRunDiagnostics: suspend () -> List<com.ghostmode.app.shell.CommandResult>,
    logEntries: List<CommandLogEntry>,
    onClearLog: () -> Unit,
    onRemoveEntry: (CommandLogEntry) -> Unit,
    isScheduleEnabled: Boolean,
    scheduleStartMinutes: Int,
    scheduleEndMinutes: Int,
    onScheduleChanged: (Boolean, Int, Int) -> Unit,
    notificationEnabled: Boolean,
    onNotificationToggled: (Boolean) -> Unit,
    simSlotMode: SimSlotMode = SimSlotMode.ALL,
    onSimSlotModeChanged: (SimSlotMode) -> Unit = {},
    onRequestAddTile: () -> Unit = {},
    sessionHistory: List<GhostSession>,
    todayTotalMs: Long,
    sevenDaysTotalMs: Long,
    allTimeTotalMs: Long,
    availableUpdate: GitHubRelease?,
    onCheckUpdates: () -> Unit,
    onDismissUpdate: () -> Unit,
    onOpenUrl: (String) -> Unit,
    themeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    isBatteryExempt: Boolean,
    onRequestIgnoreBatteryOptimization: () -> Unit,
    timerFireAtMs: Long,
    onArmTimerMinutes: (Int) -> Unit,
    onArmTimerUntilMorning: () -> Unit,
    onCancelTimer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeDialog by remember { mutableStateOf(ActiveDialog.NONE) }
    var menuExpanded by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(true) }

    var editingPreset by remember { mutableStateOf<Preset?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExportPresets) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImportPresets) }

    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_ghost),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.settings_title)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.menu_diagnostics)) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    activeDialog = ActiveDialog.DIAGNOSTICS
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.menu_logs)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    activeDialog = ActiveDialog.LOGS
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.menu_schedule)) },
                                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    activeDialog = ActiveDialog.SCHEDULE
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.menu_notification)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_ghost),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingIcon = {
                                    Switch(
                                        checked = notificationEnabled,
                                        onCheckedChange = { checked ->
                                            onNotificationToggled(checked)
                                        }
                                    )
                                },
                                onClick = {
                                    onNotificationToggled(!notificationEnabled)
                                }
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.menu_add_tile)) },
                                    leadingIcon = { Icon(Icons.Default.Build, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onRequestAddTile()
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.menu_updates)) },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    activeDialog = ActiveDialog.UPDATES
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.menu_theme)) },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    activeDialog = ActiveDialog.THEME
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.menu_language)) },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    activeDialog = ActiveDialog.LANGUAGE
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.menu_stats)) },
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    activeDialog = ActiveDialog.STATS
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.menu_about)) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    activeDialog = ActiveDialog.ABOUT
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.menu_donate)) },
                                leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    activeDialog = ActiveDialog.DONATE
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = insets.calculateStartPadding(layoutDirection) + HORIZONTAL_PADDING,
                    end = insets.calculateEndPadding(layoutDirection) + HORIZONTAL_PADDING,
                    top = contentPadding.calculateTopPadding() + VERTICAL_PADDING,
                    bottom = insets.calculateBottomPadding() + VERTICAL_PADDING
                )
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SECTION_SPACING)
        ) {
            availableUpdate?.let { release ->
                UpdateBannerCard(
                    release = release,
                    onDownload = {
                        onOpenUrl(release.apkUrl)
                        onDismissUpdate()
                    },
                    onDismiss = onDismissUpdate
                )
            }

            // Main Ghost Mode Toggle Card
            ModeControlCard(
                isOn = isOn,
                isBusy = isBusy,
                onToggle = onToggle
            )

            if (isOn) {
                TimerCard(
                    timerFireAtMs = timerFireAtMs,
                    onArmTimerMinutes = onArmTimerMinutes,
                    onArmTimerUntilMorning = onArmTimerUntilMorning,
                    onCancelTimer = onCancelTimer
                )
            }

            // Dual SIM Slot Selection Card
            SimSelectionCard(
                simSlotMode = simSlotMode,
                onSimSlotModeChanged = onSimSlotModeChanged
            )

            // Privilege Status Card (Root or Shizuku)
            PrivilegeStatusCard(
                isRootAvailable = isRootAvailable,
                shizukuStatus = shizukuStatus,
                onGrantPermission = onGrantPermission,
                onOpenShizuku = onOpenShizuku,
                onDownloadShizuku = onDownloadShizuku
            )

            if (!isBatteryExempt) {
                BatteryOptimizationCard(onRequest = onRequestIgnoreBatteryOptimization)
            }

            // Presets Header & Mode
            PresetsHeader(
                isGridView = isGridView,
                onToggleLayout = { isGridView = !isGridView },
                onNewPreset = {
                    editingPreset = null
                    isCreatingNew = true
                    activeDialog = ActiveDialog.PRESET_EDITOR
                },
                onExport = { exportLauncher.launch("ghostmode-presets.json") },
                onImport = { importLauncher.launch(arrayOf("application/json")) }
            )

            // Presets Grid or List
            if (isGridView) {
                PresetsGrid(
                    presets = presets,
                    activePresetId = activePresetId,
                    onSelectPreset = onSelectPreset,
                    onDuplicate = onDuplicatePreset,
                    onEdit = { preset ->
                        editingPreset = preset
                        isCreatingNew = false
                        activeDialog = ActiveDialog.PRESET_EDITOR
                    },
                    onDelete = onDeletePreset
                )
            } else {
                PresetsList(
                    presets = presets,
                    activePresetId = activePresetId,
                    onSelectPreset = onSelectPreset,
                    onDuplicate = onDuplicatePreset,
                    onEdit = { preset ->
                        editingPreset = preset
                        isCreatingNew = false
                        activeDialog = ActiveDialog.PRESET_EDITOR
                    },
                    onDelete = onDeletePreset
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Active Dialogs Router
    when (activeDialog) {
        ActiveDialog.DIAGNOSTICS -> {
            DiagnosticsDialog(
                isOn = isOn,
                onRunDiagnostics = onRunDiagnostics,
                onDismiss = { activeDialog = ActiveDialog.NONE },
                onViewLogs = { activeDialog = ActiveDialog.LOGS }
            )
        }
        ActiveDialog.LOGS -> {
            LogsDialog(
                logEntries = logEntries,
                onClearLog = onClearLog,
                onRemoveEntry = onRemoveEntry,
                onDismiss = { activeDialog = ActiveDialog.NONE }
            )
        }
        ActiveDialog.SCHEDULE -> {
            ScheduleDialog(
                isScheduleEnabled = isScheduleEnabled,
                scheduleStartMinutes = scheduleStartMinutes,
                scheduleEndMinutes = scheduleEndMinutes,
                onScheduleChanged = onScheduleChanged,
                onDismiss = { activeDialog = ActiveDialog.NONE }
            )
        }
        ActiveDialog.LANGUAGE -> {
            LanguageDialog(
                onDismiss = { activeDialog = ActiveDialog.NONE }
            )
        }
        ActiveDialog.THEME -> {
            ThemeDialog(
                currentMode = themeMode,
                onSelect = { mode ->
                    onThemeChanged(mode)
                    activeDialog = ActiveDialog.NONE
                },
                onDismiss = { activeDialog = ActiveDialog.NONE }
            )
        }
        ActiveDialog.UPDATES -> {
            UpdatesDialog(
                availableRelease = availableUpdate,
                onCheckUpdates = onCheckUpdates,
                onDownload = { url ->
                    onOpenUrl(url)
                    activeDialog = ActiveDialog.NONE
                },
                onDismiss = { activeDialog = ActiveDialog.NONE }
            )
        }
        ActiveDialog.STATS -> {
            StatsDialog(
                todayTotalMs = todayTotalMs,
                sevenDaysTotalMs = sevenDaysTotalMs,
                allTimeTotalMs = allTimeTotalMs,
                sessionHistory = sessionHistory,
                onDismiss = { activeDialog = ActiveDialog.NONE }
            )
        }
        ActiveDialog.ABOUT -> {
            AboutDialog(
                onDismiss = { activeDialog = ActiveDialog.NONE }
            )
        }
        ActiveDialog.DONATE -> {
            DonateDialog(
                onDismiss = { activeDialog = ActiveDialog.NONE }
            )
        }
        ActiveDialog.PRESET_EDITOR -> {
            val presetToEdit = editingPreset ?: Preset(
                id = java.util.UUID.randomUUID().toString(),
                title = "",
                description = "",
                onCommands = emptyList(),
                offCommands = emptyList(),
                networkMaskCaptureCommand = null,
                isBuiltIn = false
            )
            PresetEditorDialog(
                initialPreset = presetToEdit,
                isNewPreset = isCreatingNew,
                onSave = { preset ->
                    onSavePreset(preset)
                    activeDialog = ActiveDialog.NONE
                },
                onDismiss = { activeDialog = ActiveDialog.NONE }
            )
        }
        ActiveDialog.NONE -> Unit
    }
}

// ---------------------- Dashboard Components ----------------------

@Composable
private fun ModeControlCard(
    isOn: Boolean,
    isBusy: Boolean,
    onToggle: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(if (isOn) R.string.status_title_on else R.string.status_title_off),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isOn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(if (isOn) R.string.status_subtitle_on else R.string.status_subtitle_off),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isOn) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }

                Switch(
                    checked = isOn,
                    onCheckedChange = { onToggle() },
                    enabled = !isBusy
                )
            }

            AnimatedVisibility(visible = isBusy) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.busy_message),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivilegeStatusCard(
    isRootAvailable: Boolean,
    shizukuStatus: ShizukuStatus,
    onGrantPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onDownloadShizuku: () -> Unit
) {
    if (isRootAvailable) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.root_status_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Text(
                    text = stringResource(R.string.root_status_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        ShizukuStatusCard(
            status = shizukuStatus,
            onGrantPermission = onGrantPermission,
            onOpenShizuku = onOpenShizuku,
            onDownloadShizuku = onDownloadShizuku
        )
    }
}

@Composable
private fun ShizukuStatusCard(
    status: ShizukuStatus,
    onGrantPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onDownloadShizuku: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                ShizukuStatus.READY -> MaterialTheme.colorScheme.surfaceContainerHighest
                else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (status == ShizukuStatus.READY) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (status == ShizukuStatus.READY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.shizuku_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Text(
                text = when (status) {
                    ShizukuStatus.NOT_INSTALLED -> stringResource(R.string.shizuku_status_not_installed)
                    ShizukuStatus.NOT_RUNNING -> stringResource(R.string.shizuku_status_not_running)
                    ShizukuStatus.NO_PERMISSION -> stringResource(R.string.shizuku_status_no_permission)
                    ShizukuStatus.READY -> stringResource(R.string.shizuku_status_ready)
                },
                style = MaterialTheme.typography.bodyMedium
            )

            when (status) {
                ShizukuStatus.NOT_INSTALLED -> {
                    Text(
                        text = stringResource(R.string.shizuku_install_guide),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onDownloadShizuku,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.action_download_shizuku))
                    }
                }
                ShizukuStatus.NO_PERMISSION -> {
                    Text(
                        text = stringResource(R.string.shizuku_authorized_apps_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onGrantPermission,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.action_grant_permission))
                        }
                        OutlinedButton(
                            onClick = onOpenShizuku,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.action_open_shizuku))
                        }
                    }
                }
                ShizukuStatus.NOT_RUNNING -> {
                    Button(
                        onClick = onOpenShizuku,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.action_open_shizuku))
                    }
                }
                ShizukuStatus.READY -> Unit
            }
        }
    }
}

// ---------------------- Presets Section ----------------------

@Composable
private fun PresetsHeader(
    isGridView: Boolean,
    onToggleLayout: () -> Unit,
    onNewPreset: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.presets_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleLayout) {
                Icon(
                    imageVector = if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Default.Menu,
                    contentDescription = stringResource(if (isGridView) R.string.preset_layout_list else R.string.preset_layout_grid),
                    modifier = Modifier.size(20.dp)
                )
            }
            TextButton(
                onClick = onExport,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(text = stringResource(R.string.presets_action_export), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = onImport,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(text = stringResource(R.string.presets_action_import), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = onNewPreset,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(text = "+ " + stringResource(R.string.editor_title_new), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PresetsGrid(
    presets: List<Preset>,
    activePresetId: String,
    onSelectPreset: (String) -> Unit,
    onDuplicate: (Preset) -> Unit,
    onEdit: (Preset) -> Unit,
    onDelete: (String) -> Unit
) {
    val chunkedPresets = remember(presets) { presets.chunked(2) }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        chunkedPresets.forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowPresets.forEach { preset ->
                    Box(modifier = Modifier.weight(1f)) {
                        PresetTileCard(
                            preset = preset,
                            isSelected = preset.id == activePresetId,
                            onSelect = { onSelectPreset(preset.id) },
                            onDuplicate = { onDuplicate(preset) },
                            onEdit = { onEdit(preset) },
                            onDelete = { onDelete(preset.id) }
                        )
                    }
                }
                if (rowPresets.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PresetTileCard(
    preset: Preset,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = stringResource(if (preset.isBuiltIn) R.string.preset_builtin else R.string.preset_custom),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (preset.isBuiltIn) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    border = null,
                    modifier = Modifier.height(24.dp)
                )

                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_duplicate)) },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            }
                        )
                        if (!preset.isBuiltIn) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_edit)) },
                                onClick = {
                                    menuOpen = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            Text(
                text = preset.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.preset_commands_count, preset.onCommands.size + preset.offCommands.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetsList(
    presets: List<Preset>,
    activePresetId: String,
    onSelectPreset: (String) -> Unit,
    onDuplicate: (Preset) -> Unit,
    onEdit: (Preset) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        presets.forEach { preset ->
            PresetCard(
                preset = preset,
                isSelected = preset.id == activePresetId,
                onSelect = { onSelectPreset(preset.id) },
                onDuplicate = { onDuplicate(preset) },
                onEdit = { onEdit(preset) },
                onDelete = { onDelete(preset.id) }
            )
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
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = stringResource(if (preset.isBuiltIn) R.string.preset_builtin else R.string.preset_custom),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (preset.isBuiltIn) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    border = null
                )
            }

            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDuplicate) {
                    Text(stringResource(R.string.action_duplicate))
                }
                if (!preset.isBuiltIn) {
                    TextButton(onClick = onEdit) {
                        Text(stringResource(R.string.action_edit))
                    }
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ---------------------- Dialogs ----------------------

@Composable
private fun DiagnosticsDialog(
    isOn: Boolean,
    onRunDiagnostics: suspend () -> List<com.ghostmode.app.shell.CommandResult>,
    onDismiss: () -> Unit,
    onViewLogs: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<com.ghostmode.app.shell.CommandResult>?>(null) }

    val diagnosticState = remember(results, isOn) {
        parseNetworkDiagnostics(results, isOn)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.menu_diagnostics))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Main Call Status Card
                if (diagnosticState == null) {
                    Text(
                        text = stringResource(R.string.diagnostics_not_run_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (diagnosticState != null) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (diagnosticState.areCallsBlocked) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (diagnosticState.areCallsBlocked) Icons.Default.Close else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.diag_calls_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(
                                        if (diagnosticState.areCallsBlocked) R.string.diag_calls_blocked else R.string.diag_calls_allowed
                                    ),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // Details rows
                        DiagRow(
                            label = stringResource(R.string.diag_internet_label),
                            value = stringResource(R.string.diag_internet_active)
                        )
                        DiagRow(
                            label = stringResource(R.string.diag_modem_label),
                            value = if (diagnosticState.is2G3GDisabled) {
                                stringResource(R.string.diag_modem_lte_only)
                            } else {
                                stringResource(R.string.diag_modem_all_networks)
                            }
                        )
                        DiagRow(
                            label = stringResource(R.string.diag_ims_label),
                            value = if (diagnosticState.isImsDisabled) {
                                stringResource(R.string.diag_ims_disabled)
                            } else {
                                stringResource(R.string.diag_ims_enabled)
                            }
                        )
                    }
                }

                // Status message explanation
                Text(
                    text = if (diagnosticState.areCallsBlocked) {
                        stringResource(R.string.diag_summary_blocked)
                    } else {
                        stringResource(R.string.diag_summary_allowed)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                }

                // Refresh Button
                Button(
                    onClick = {
                        scope.launch {
                            isRunning = true
                            results = onRunDiagnostics()
                            isRunning = false
                        }
                    },
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.diagnostics_running))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.action_run_diagnostics))
                    }
                }

                // Command log button
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onViewLogs()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.menu_logs))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dialog_close))
            }
        }
    )
}

private data class CallStatusDiagnostic(
    val areCallsBlocked: Boolean,
    val isInternetWorking: Boolean,
    val is2G3GDisabled: Boolean,
    val isImsDisabled: Boolean
)

private fun parseNetworkDiagnostics(
    results: List<com.ghostmode.app.shell.CommandResult>?,
    isGhostModeOn: Boolean
): CallStatusDiagnostic? {
    if (results == null) {
        return null
    }

    val maskOutput = results.find { it.command.contains("get-allowed-network-types") }?.stdout.orEmpty()
    val hasLegacyNetworks = maskOutput.contains("GSM", ignoreCase = true) ||
            maskOutput.contains("UMTS", ignoreCase = true) ||
            maskOutput.contains("GPRS", ignoreCase = true) ||
            maskOutput.contains("EDGE", ignoreCase = true) ||
            maskOutput.contains("CDMA", ignoreCase = true)

    val imsOutput = results.filter { it.command.contains("ims") }.joinToString(" ") { it.stdout + " " + it.stderr }
    val isImsDisabled = isGhostModeOn ||
            imsOutput.contains("disabled", ignoreCase = true) ||
            imsOutput.contains("Can't find service", ignoreCase = true) ||
            imsOutput.contains("null", ignoreCase = true)

    val is2G3GDisabled = isGhostModeOn || (!hasLegacyNetworks && maskOutput.isNotBlank())
    val areCallsBlocked = isGhostModeOn || (isImsDisabled && is2G3GDisabled)

    return CallStatusDiagnostic(
        areCallsBlocked = areCallsBlocked,
        isInternetWorking = true,
        is2G3GDisabled = is2G3GDisabled,
        isImsDisabled = isImsDisabled
    )
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.2f, fill = false)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun LogsDialog(
    logEntries: List<CommandLogEntry>,
    onClearLog: () -> Unit,
    onRemoveEntry: (CommandLogEntry) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.menu_logs),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dialog_close))
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        LogPanel(
                            entries = logEntries,
                            onClear = onClearLog,
                            onRemoveEntry = onRemoveEntry
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.dialog_close))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDialog(
    isScheduleEnabled: Boolean,
    scheduleStartMinutes: Int,
    scheduleEndMinutes: Int,
    onScheduleChanged: (Boolean, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var enabled by remember { mutableStateOf(isScheduleEnabled) }
    var startMinutes by remember { mutableIntStateOf(scheduleStartMinutes) }
    var endMinutes by remember { mutableIntStateOf(scheduleEndMinutes) }
    var pickingTimeFor by remember { mutableStateOf<String?>(null) }

    val startTimeState = rememberTimePickerState(
        initialHour = startMinutes / 60,
        initialMinute = startMinutes % 60,
        is24Hour = true
    )
    val endTimeState = rememberTimePickerState(
        initialHour = endMinutes / 60,
        initialMinute = endMinutes % 60,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.schedule_title))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.schedule_enabled),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }

                AnimatedVisibility(visible = enabled) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { pickingTimeFor = "START" },
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = stringResource(R.string.schedule_from), style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        text = String.format("%02d:%02d", startMinutes / 60, startMinutes % 60),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { pickingTimeFor = "END" },
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = stringResource(R.string.schedule_to), style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        text = String.format("%02d:%02d", endMinutes / 60, endMinutes % 60),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.schedule_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onScheduleChanged(enabled, startMinutes, endMinutes)
                    onDismiss()
                }
            ) {
                Text(text = stringResource(R.string.schedule_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.schedule_cancel))
            }
        }
    )

    if (pickingTimeFor != null) {
        val isStart = pickingTimeFor == "START"
        val state = if (isStart) startTimeState else endTimeState

        AlertDialog(
            onDismissRequest = { pickingTimeFor = null },
            title = {
                Text(text = stringResource(if (isStart) R.string.schedule_from else R.string.schedule_to))
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = state)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isStart) {
                            startMinutes = state.hour * 60 + state.minute
                        } else {
                            endMinutes = state.hour * 60 + state.minute
                        }
                        pickingTimeFor = null
                    }
                ) {
                    Text(text = stringResource(R.string.schedule_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingTimeFor = null }) {
                    Text(text = stringResource(R.string.schedule_cancel))
                }
            }
        )
    }
}

@Composable
private fun LanguageDialog(
    onDismiss: () -> Unit
) {
    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.settings_language))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList())
                            onDismiss()
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentTag.isEmpty(),
                        onClick = {
                            AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList())
                            onDismiss()
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.language_system))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags("ru"))
                            onDismiss()
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentTag.startsWith("ru"),
                        onClick = {
                            AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags("ru"))
                            onDismiss()
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.language_russian))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags("en"))
                            onDismiss()
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentTag.startsWith("en"),
                        onClick = {
                            AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags("en"))
                            onDismiss()
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.language_english))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dialog_close))
            }
        }
    )
}

@Composable
private fun UpdateBannerCard(
    release: GitHubRelease,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.update_banner_new, release.versionName),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            release.changelog?.takeIf { it.isNotBlank() }?.let { changelog ->
                Text(
                    text = changelog,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.update_download))
                }
                OutlinedButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.update_later))
                }
            }
        }
    }
}

@Composable
private fun BatteryOptimizationCard(onRequest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.battery_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            Text(
                text = stringResource(R.string.battery_text),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.battery_action))
            }
        }
    }
}

@Composable
private fun TimerCard(
    timerFireAtMs: Long,
    onArmTimerMinutes: (Int) -> Unit,
    onArmTimerUntilMorning: () -> Unit,
    onCancelTimer: () -> Unit
) {
    val isArmed = timerFireAtMs > System.currentTimeMillis()
    val tick by produceState(System.currentTimeMillis(), timerFireAtMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(TIMER_TICK_INTERVAL_MS)
        }
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.timer_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            if (isArmed) {
                val targetClock = remember(timerFireAtMs) { formatClockTime(timerFireAtMs) }
                val minutesLeft = ((timerFireAtMs - tick) / 60_000L).coerceAtLeast(0L)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.timer_armed_in, targetClock),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = stringResource(R.string.timer_minutes_left, formatDuration(minutesLeft * 60_000L)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onCancelTimer) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.timer_cancel)
                        )
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { onArmTimerMinutes(TIMER_CHIP_30M_MINUTES) },
                        label = { Text(text = stringResource(R.string.timer_chip_30m)) }
                    )
                    AssistChip(
                        onClick = { onArmTimerMinutes(TIMER_CHIP_1H_MINUTES) },
                        label = { Text(text = stringResource(R.string.timer_chip_1h)) }
                    )
                    AssistChip(
                        onClick = { onArmTimerMinutes(TIMER_CHIP_2H_MINUTES) },
                        label = { Text(text = stringResource(R.string.timer_chip_2h)) }
                    )
                    AssistChip(
                        onClick = onArmTimerUntilMorning,
                        label = { Text(text = stringResource(R.string.timer_chip_morning)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeDialog(
    currentMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.menu_theme))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.DARK to R.string.theme_dark,
                    ThemeMode.LIGHT to R.string.theme_light
                ).forEach { (mode, labelRes) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentMode == mode, onClick = { onSelect(mode) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(labelRes))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dialog_close))
            }
        }
    )
}

@Composable
private fun UpdatesDialog(
    availableRelease: GitHubRelease?,
    onCheckUpdates: () -> Unit,
    onDownload: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.menu_updates))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (availableRelease == null) {
                    Text(
                        text = stringResource(R.string.update_none_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = stringResource(R.string.update_banner_new, availableRelease.versionName),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    availableRelease.changelog?.takeIf { it.isNotBlank() }?.let { changelog ->
                        HorizontalDivider()
                        Text(
                            text = changelog,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { onDownload(availableRelease.apkUrl) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.update_download))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCheckUpdates) {
                Text(text = stringResource(R.string.update_check_again))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dialog_close))
            }
        }
    )
}

private fun formatClockTime(timeMs: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timeMs
    return String.format(
        "%02d:%02d",
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE)
    )
}

@Composable
private fun StatsDialog(
    todayTotalMs: Long,
    sevenDaysTotalMs: Long,
    allTimeTotalMs: Long,
    sessionHistory: List<GhostSession>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.stats_title))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${stringResource(R.string.stats_today)}: ${formatDuration(todayTotalMs)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${stringResource(R.string.stats_week)}: ${formatDuration(sevenDaysTotalMs)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${stringResource(R.string.stats_all)}: ${formatDuration(allTimeTotalMs)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = String.format(stringResource(R.string.stats_activations), sessionHistory.size),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dialog_close))
            }
        }
    )
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_ghost),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.about_title))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_version_label),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                Text(
                    text = stringResource(R.string.about_cert_label),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Text(
                        text = RELEASE_CERT_SHA256,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Foxlape/GhostMode"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.about_github))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dialog_close))
            }
        }
    )
}

@Composable
private fun DonateDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.settings_donate_title))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.donate_description),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (DONATE_URL.isNotBlank()) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DONATE_URL))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.settings_donate_title))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dialog_close))
            }
        }
    )
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimSelectionCard(
    simSlotMode: SimSlotMode,
    onSimSlotModeChanged: (SimSlotMode) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.sim_selection_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                val modes = listOf(
                    SimSlotMode.ALL to stringResource(R.string.sim_slot_all),
                    SimSlotMode.SIM_1 to stringResource(R.string.sim_slot_sim_1),
                    SimSlotMode.SIM_2 to stringResource(R.string.sim_slot_sim_2)
                )
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = simSlotMode == mode,
                        onClick = { onSimSlotModeChanged(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (simSlotMode == mode) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            val descriptionRes = when (simSlotMode) {
                SimSlotMode.ALL -> R.string.sim_slot_all_desc
                SimSlotMode.SIM_1 -> R.string.sim_slot_1_desc
                SimSlotMode.SIM_2 -> R.string.sim_slot_2_desc
            }
            Text(
                text = stringResource(descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

