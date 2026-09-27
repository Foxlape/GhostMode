package com.ghostmode.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostmode.app.R
import com.ghostmode.app.domain.NetworkMask
import com.ghostmode.app.domain.SlotDiagnostics
import com.ghostmode.app.ui.MainViewModel
import com.ghostmode.app.ui.MobileDataStatus
import com.ghostmode.app.ui.components.CodeBlock
import com.ghostmode.app.ui.components.GhostCard
import com.ghostmode.app.ui.components.IconBadge
import com.ghostmode.app.ui.components.ScreenPadding
import com.ghostmode.app.ui.components.SectionHeader
import com.ghostmode.app.ui.components.SubScreenTopBar
import com.ghostmode.app.ui.theme.LocalGhostColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(vm: MainViewModel, onBack: () -> Unit, onOpenLog: () -> Unit) {
    val state by vm.diagnostics.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var confirmRepair by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (state.report == null && !state.isRunning) vm.runDiagnostics() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { SubScreenTopBar(stringResource(R.string.diagnostics_title), onBack, scrollBehavior) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val report = state.report
            when {
                state.isRunning && report == null -> Row(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.diagnostics_running))
                }
                state.failed -> GhostCard {
                    Text(stringResource(R.string.diagnostics_failed), style = MaterialTheme.typography.bodyLarge)
                }
                report != null -> {
                    VerdictCard(report.callsLikelyBlocked)
                    SectionHeader(stringResource(R.string.diagnostics_details))
                    GhostCard {
                        report.slots.forEachIndexed { index, slot ->
                            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                            SlotRows(slot, showSlotTitle = report.slots.size > 1)
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        DiagRow(
                            stringResource(R.string.diag_mobile_data),
                            stringResource(
                                when (state.mobileData) {
                                    MobileDataStatus.ACTIVE -> R.string.diag_mobile_data_active
                                    MobileDataStatus.STANDBY_WIFI -> R.string.diag_mobile_data_wifi
                                    else -> R.string.diag_mobile_data_none
                                }
                            ),
                            good = state.mobileData != MobileDataStatus.UNAVAILABLE
                        )
                    }
                    if (report.disabledImsPackages.isNotEmpty()) {
                        SectionHeader(stringResource(R.string.diag_disabled_packages))
                        GhostCard {
                            Text(
                                stringResource(R.string.diag_disabled_packages_text),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            CodeBlock(report.disabledImsPackages.joinToString("\n"))
                            Spacer(Modifier.height(10.dp))
                            FilledTonalButton(onClick = { confirmRepair = true }) {
                                Icon(Icons.Outlined.Build, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.diag_enable_packages))
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.diagnostics_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = vm::runDiagnostics, enabled = !state.isRunning, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.diagnostics_run))
                }
                OutlinedButton(onClick = onOpenLog, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Terminal, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.log_title))
                }
            }
        }
    }

    if (confirmRepair) {
        val packages = state.report?.disabledImsPackages.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmRepair = false },
            title = { Text(stringResource(R.string.diag_enable_packages)) },
            text = { Text(stringResource(R.string.diag_enable_packages_confirm, packages.joinToString(", "))) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRepair = false
                    vm.enablePackages(packages)
                }) { Text(stringResource(R.string.action_enable)) }
            },
            dismissButton = { TextButton(onClick = { confirmRepair = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun VerdictCard(blocked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    GhostCard(color = if (blocked) scheme.primaryContainer else scheme.surfaceContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                if (blocked) Icons.Outlined.CallEnd else Icons.Outlined.Call,
                tint = if (blocked) scheme.primary else LocalGhostColors.current.idleAccent,
                size = 48
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.diag_calls_label), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                Text(
                    stringResource(if (blocked) R.string.diag_calls_blocked else R.string.diag_calls_allowed),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(if (blocked) R.string.diag_summary_blocked else R.string.diag_summary_allowed),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SlotRows(slot: SlotDiagnostics, showSlotTitle: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showSlotTitle) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhoneInTalk, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.diag_slot, slot.slot + 1), style = MaterialTheme.typography.titleSmall)
            }
        }
        DiagRow(
            stringResource(R.string.diag_network_label),
            when (slot.blocksLegacyVoice) {
                true -> stringResource(R.string.diag_network_lte_only)
                false -> stringResource(R.string.diag_network_all)
                null -> stringResource(R.string.diag_unknown)
            },
            good = slot.blocksLegacyVoice,
            detail = slot.networkMask?.let { mask -> networkTypesText(mask) }
        )
        DiagRow(
            stringResource(R.string.diag_ims_label),
            if (slot.boundImsPackages.isEmpty()) stringResource(R.string.diag_ims_not_bound) else stringResource(R.string.diag_ims_bound),
            good = slot.boundImsPackages.isEmpty(),
            detail = slot.boundImsPackages.joinToString(", ").ifEmpty { null }
        )
    }
}

/** A labelled value; [good] colors the value (ghost-mode perspective), `null` keeps it neutral. */
@Composable
private fun DiagRow(label: String, value: String, good: Boolean?, detail: String? = null) {
    val colors = LocalGhostColors.current
    Row(verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1.2f)) {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = when (good) {
                    true -> colors.success
                    false -> MaterialTheme.colorScheme.onSurface
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val TYPE_NAMES = mapOf(
    1 to "GPRS", 2 to "EDGE", 3 to "UMTS", 4 to "CDMA", 5 to "EvDo0", 6 to "EvDoA", 7 to "1xRTT",
    8 to "HSDPA", 9 to "HSUPA", 10 to "HSPA", 11 to "iDEN", 12 to "EvDoB", 13 to "LTE", 14 to "eHRPD",
    15 to "HSPA+", 16 to "GSM", 17 to "TD-SCDMA", 18 to "IWLAN", 19 to "LTE-CA", 20 to "NR"
)

private fun networkTypesText(mask: String): String =
    NetworkMask.toTypes(mask).mapNotNull { TYPE_NAMES[it] }.joinToString(" · ")
