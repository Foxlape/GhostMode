package com.ghostmode.app.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Troubleshoot
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostmode.app.R
import com.ghostmode.app.data.SessionStats
import com.ghostmode.app.data.SimSlotMode
import com.ghostmode.app.shell.ShellBackend
import com.ghostmode.app.shell.ShizukuStatus
import com.ghostmode.app.support.UpdateState
import com.ghostmode.app.ui.Format
import com.ghostmode.app.ui.MainViewModel
import com.ghostmode.app.ui.components.AlertCard
import com.ghostmode.app.ui.components.GhostCard
import com.ghostmode.app.ui.components.IconBadge
import com.ghostmode.app.ui.components.InfoChip
import com.ghostmode.app.ui.components.ScreenPadding
import com.ghostmode.app.ui.components.StatusPill
import com.ghostmode.app.ui.presetTitle
import com.ghostmode.app.ui.theme.LocalGhostColors
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onOpenPresets: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenStats: () -> Unit,
    onEditSchedule: () -> Unit,
    onEditSim: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val isOn by vm.isOn.collectAsStateWithLifecycle()
    val isBusy by vm.isBusy.collectAsStateWithLifecycle()
    val backend by vm.backend.collectAsStateWithLifecycle()
    val shizukuStatus by vm.shizukuStatus.collectAsStateWithLifecycle()
    val needsReapply by vm.needsReapply.collectAsStateWithLifecycle()
    val sinceMs by vm.isOnSinceMs.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val activePresetId by vm.activePresetId.collectAsStateWithLifecycle()
    val snapshot by vm.appliedSnapshot.collectAsStateWithLifecycle()
    val simMode by vm.simSlotMode.collectAsStateWithLifecycle()
    val timerFireAt by vm.timerFireAtMs.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val scheduleEnabled by vm.scheduleEnabled.collectAsStateWithLifecycle()
    val scheduleStart by vm.scheduleStart.collectAsStateWithLifecycle()
    val scheduleEnd by vm.scheduleEnd.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val systemStatus by vm.systemStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val now by produceState(System.currentTimeMillis(), isOn, timerFireAt) {
        while (true) {
            value = System.currentTimeMillis()
            delay(TICK_MS)
        }
    }

    val appliedPresetId = if (isOn) snapshot?.presetId ?: activePresetId else activePresetId
    val preset = presets.firstOrNull { it.id == appliedPresetId }
    val hasBackend = backend != null
    val timerArmed = timerFireAt > now

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Header(backend = backend, shizukuStatus = shizukuStatus)

        if (needsReapply) {
            AlertCard(
                icon = Icons.Outlined.RestartAlt,
                title = stringResource(R.string.alert_reapply_title),
                text = stringResource(R.string.alert_reapply_text),
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer
            ) {
                TextButton(onClick = vm::turnOff, enabled = !isBusy) { Text(stringResource(R.string.action_turn_off)) }
                Button(onClick = vm::reapply, enabled = !isBusy) { Text(stringResource(R.string.action_reapply)) }
            }
        }

        if (!hasBackend) BackendAlert(shizukuStatus, vm)

        (updateState as? UpdateState.Available)?.let { available ->
            AlertCard(
                icon = Icons.Outlined.NewReleases,
                title = stringResource(R.string.alert_update_title, available.release.versionName),
                text = stringResource(R.string.alert_update_text),
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurface
            ) {
                TextButton(onClick = vm::dismissUpdate) { Text(stringResource(R.string.action_later)) }
                Button(onClick = {
                    onOpenUrl(available.release.pageUrl)
                    vm.dismissUpdate()
                }) { Text(stringResource(R.string.action_open_release)) }
            }
        }

        val needsAlarms = scheduleEnabled || timerArmed
        if (needsAlarms && !systemStatus.canScheduleExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlertCard(
                icon = Icons.Outlined.AlarmOff,
                title = stringResource(R.string.alert_alarms_title),
                text = stringResource(R.string.alert_alarms_text),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                FilledTonalButton(onClick = {
                    context.startActivitySafely(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    )
                }) { Text(stringResource(R.string.action_allow)) }
            }
        } else if (needsAlarms && !systemStatus.isBatteryExempt) {
            AlertCard(
                icon = Icons.Outlined.BatteryAlert,
                title = stringResource(R.string.alert_battery_title),
                text = stringResource(R.string.alert_battery_text),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                FilledTonalButton(onClick = { context.requestIgnoreBatteryOptimizations() }) {
                    Text(stringResource(R.string.action_allow))
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GhostHero(
                isOn = isOn,
                isBusy = isBusy,
                enabled = hasBackend || isOn,
                stateDescription = stringResource(if (isOn) R.string.status_title_on else R.string.status_title_off),
                onClick = vm::toggle
            )
            Text(
                text = stringResource(
                    when {
                        isBusy -> R.string.status_title_busy
                        isOn -> R.string.status_title_on
                        else -> R.string.status_title_off
                    }
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    when {
                        !hasBackend && !isOn -> R.string.status_subtitle_no_access
                        isOn -> R.string.status_subtitle_on
                        else -> R.string.status_subtitle_off
                    }
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(16.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                InfoChip(Icons.Outlined.Layers, preset?.let { presetTitle(it) } ?: "—")
                InfoChip(Icons.Outlined.SimCard, stringResource(simLabel(snapshot?.slots?.let(::simModeOf) ?: simMode)))
                if (isOn && sinceMs > 0) {
                    InfoChip(Icons.Outlined.Timer, stringResource(R.string.chip_active_for, Format.duration(context, now - sinceMs)))
                }
            }
        }

        AnimatedVisibility(visible = isOn) {
            TimerCard(
                timerFireAt = timerFireAt,
                now = now,
                onArmMinutes = vm::armTimerMinutes,
                onArmMorning = vm::armTimerUntilMorning,
                onCancel = vm::cancelTimer
            )
        }

        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickTile(
                icon = Icons.Outlined.SimCard,
                title = stringResource(R.string.tile_sim),
                value = stringResource(simLabel(simMode)),
                onClick = onEditSim,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            QuickTile(
                icon = Icons.Outlined.Schedule,
                title = stringResource(R.string.tile_schedule),
                value = if (scheduleEnabled) {
                    "${Format.minuteOfDay(context, scheduleStart)} – ${Format.minuteOfDay(context, scheduleEnd)}"
                } else {
                    stringResource(R.string.value_off)
                },
                onClick = onEditSchedule,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickTile(
                icon = Icons.Outlined.BarChart,
                title = stringResource(R.string.tile_today),
                value = Format.duration(context, SessionStats.durationInWindow(sessions, SessionStats.startOfDay(now), now)),
                onClick = onOpenStats,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            QuickTile(
                icon = Icons.Outlined.Troubleshoot,
                title = stringResource(R.string.tile_diagnostics),
                value = stringResource(R.string.tile_diagnostics_value),
                onClick = onOpenDiagnostics,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        GhostCard(onClick = onOpenPresets) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.Layers)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_preset_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(preset?.let { presetTitle(it) } ?: "—", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    stringResource(R.string.action_change),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Header(backend: ShellBackend?, shizukuStatus: ShizukuStatus) {
    val ghostColors = LocalGhostColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f)
        )
        val (label, color) = when (backend) {
            ShellBackend.ROOT -> stringResource(R.string.backend_root) to ghostColors.success
            ShellBackend.SHIZUKU -> stringResource(R.string.backend_shizuku) to ghostColors.success
            null -> stringResource(
                if (shizukuStatus == ShizukuStatus.NO_PERMISSION) R.string.backend_no_permission else R.string.backend_none
            ) to MaterialTheme.colorScheme.error
        }
        StatusPill(text = label, dotColor = color)
    }
}

@Composable
private fun BackendAlert(status: ShizukuStatus, vm: MainViewModel) {
    val (title, text) = when (status) {
        ShizukuStatus.NOT_INSTALLED -> R.string.alert_access_title to R.string.alert_access_not_installed
        ShizukuStatus.NOT_RUNNING -> R.string.alert_shizuku_stopped_title to R.string.alert_shizuku_stopped_text
        ShizukuStatus.NO_PERMISSION -> R.string.alert_shizuku_permission_title to R.string.alert_shizuku_permission_text
        ShizukuStatus.READY -> return
    }
    AlertCard(
        icon = Icons.Outlined.Key,
        title = stringResource(title),
        text = stringResource(text),
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        when (status) {
            ShizukuStatus.NOT_INSTALLED -> Button(onClick = vm::downloadShizuku) { Text(stringResource(R.string.action_get_shizuku)) }
            ShizukuStatus.NOT_RUNNING -> Button(onClick = vm::openShizuku) { Text(stringResource(R.string.action_open_shizuku)) }
            ShizukuStatus.NO_PERMISSION -> {
                TextButton(onClick = vm::openShizuku) { Text(stringResource(R.string.action_open_shizuku)) }
                Button(onClick = vm::requestShizukuPermission) { Text(stringResource(R.string.action_grant)) }
            }
            ShizukuStatus.READY -> Unit
        }
    }
}

@Composable
private fun TimerCard(
    timerFireAt: Long,
    now: Long,
    onArmMinutes: (Int) -> Unit,
    onArmMorning: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    GhostCard(modifier = Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.Timer)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.timer_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (timerFireAt > now) {
                        stringResource(
                            R.string.timer_armed,
                            Format.clock(context, timerFireAt),
                            Format.duration(context, timerFireAt - now)
                        )
                    } else {
                        stringResource(R.string.timer_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (timerFireAt > now) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.timer_cancel))
                }
            }
        }
        if (timerFireAt <= now) {
            Spacer(Modifier.height(12.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AssistChip(onClick = { onArmMinutes(30) }, label = { Text(stringResource(R.string.timer_30m)) })
                AssistChip(onClick = { onArmMinutes(60) }, label = { Text(stringResource(R.string.timer_1h)) })
                AssistChip(onClick = { onArmMinutes(120) }, label = { Text(stringResource(R.string.timer_2h)) })
                AssistChip(onClick = onArmMorning, label = { Text(stringResource(R.string.timer_morning)) })
            }
        }
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GhostCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        IconBadge(icon, size = 36)
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 2)
    }
}

fun simLabel(mode: SimSlotMode): Int = when (mode) {
    SimSlotMode.ALL -> R.string.sim_both
    SimSlotMode.SIM_1 -> R.string.sim_1
    SimSlotMode.SIM_2 -> R.string.sim_2
}

private fun simModeOf(slots: List<Int>): SimSlotMode =
    SimSlotMode.entries.firstOrNull { it.slots == slots } ?: SimSlotMode.ALL

fun android.content.Context.startActivitySafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

fun android.content.Context.requestIgnoreBatteryOptimizations() {
    try {
        startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        startActivitySafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

private const val TICK_MS = 15_000L
