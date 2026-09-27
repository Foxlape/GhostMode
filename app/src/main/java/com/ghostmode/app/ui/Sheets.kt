package com.ghostmode.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import android.text.format.DateFormat
import com.ghostmode.app.R
import com.ghostmode.app.data.SimSlotMode
import com.ghostmode.app.scheduling.ScheduleWindow
import com.ghostmode.app.ui.home.simLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimSheet(
    current: SimSlotMode,
    locked: Boolean,
    onSelect: (SimSlotMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.sim_sheet_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(if (locked) R.string.message_sim_locked else R.string.sim_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            SimSlotMode.entries.forEach { mode ->
                val description = when (mode) {
                    SimSlotMode.ALL -> R.string.sim_both_desc
                    SimSlotMode.SIM_1 -> R.string.sim_1_desc
                    SimSlotMode.SIM_2 -> R.string.sim_2_desc
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = mode == current,
                            enabled = !locked,
                            role = Role.RadioButton,
                            onClick = {
                                onSelect(mode)
                                onDismiss()
                            }
                        )
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = mode == current, onClick = null, enabled = !locked)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(simLabel(mode)), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSheet(
    enabled: Boolean,
    startMinute: Int,
    endMinute: Int,
    onApply: (Boolean, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isEnabled by rememberSaveable { mutableStateOf(enabled) }
    var start by rememberSaveable { mutableIntStateOf(startMinute) }
    var end by rememberSaveable { mutableIntStateOf(endMinute) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.schedule_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.schedule_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeBox(stringResource(R.string.schedule_from), Format.minuteOfDay(context, start), isEnabled, Modifier.weight(1f)) { picking = "start" }
                TimeBox(stringResource(R.string.schedule_to), Format.minuteOfDay(context, end), isEnabled, Modifier.weight(1f)) { picking = "end" }
            }
            val length = ScheduleWindow.durationMinutes(start, end)
            Text(
                if (start == end) stringResource(R.string.schedule_empty_window)
                else stringResource(R.string.schedule_summary, Format.duration(context, length * 60_000L)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (start == end) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.schedule_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    enabled = !isEnabled || start != end,
                    onClick = {
                        onApply(isEnabled, start, end)
                        onDismiss()
                    }
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }

    picking?.let { which ->
        val initial = if (which == "start") start else end
        val pickerState = rememberTimePickerState(
            initialHour = initial / 60,
            initialMinute = initial % 60,
            is24Hour = DateFormat.is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(stringResource(if (which == "start") R.string.schedule_from else R.string.schedule_to)) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val minute = pickerState.hour * 60 + pickerState.minute
                    if (which == "start") start = minute else end = minute
                    picking = null
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun TimeBox(label: String, value: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(modifier = modifier.clickable(enabled = enabled, onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}
