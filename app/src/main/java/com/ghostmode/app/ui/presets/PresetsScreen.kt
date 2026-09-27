package com.ghostmode.app.ui.presets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostmode.app.R
import com.ghostmode.app.data.Preset
import com.ghostmode.app.ui.MainViewModel
import com.ghostmode.app.ui.components.AlertCard
import com.ghostmode.app.ui.components.CodeBlock
import com.ghostmode.app.ui.components.ScreenPadding
import com.ghostmode.app.ui.presetDescription
import com.ghostmode.app.ui.presetTitle

@Composable
fun PresetsScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onImport: () -> Unit,
    onExport: () -> Unit
) {
    val presets by vm.presets.collectAsStateWithLifecycle()
    val activeId by vm.activePresetId.collectAsStateWithLifecycle()
    val isOn by vm.isOn.collectAsStateWithLifecycle()
    val snapshot by vm.appliedSnapshot.collectAsStateWithLifecycle()
    val pendingImport by vm.pendingImport.collectAsStateWithLifecycle()

    var editing by rememberSaveable(stateSaver = PresetSaver) { mutableStateOf<Preset?>(null) }
    var deleting by rememberSaveable(stateSaver = PresetSaver) { mutableStateOf<Preset?>(null) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val selectedId = if (isOn) snapshot?.presetId ?: activeId else activeId
    val duplicateSuffix = stringResource(R.string.preset_copy_suffix)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.presets_title), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            stringResource(R.string.presets_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.presets_import)) },
                                leadingIcon = { Icon(Icons.Outlined.FileDownload, null) },
                                onClick = { menuOpen = false; onImport() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.presets_export)) },
                                leadingIcon = { Icon(Icons.Outlined.FileUpload, null) },
                                enabled = presets.any { !it.isBuiltIn },
                                onClick = { menuOpen = false; onExport() }
                            )
                        }
                    }
                }
            }
            if (isOn) {
                item {
                    AlertCard(
                        icon = Icons.Outlined.Lock,
                        title = stringResource(R.string.presets_locked_title),
                        text = stringResource(R.string.presets_locked_text),
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        content = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            items(presets, key = { it.id }) { preset ->
                PresetCard(
                    preset = preset,
                    selected = preset.id == selectedId,
                    locked = isOn,
                    onSelect = { vm.selectPreset(preset.id) },
                    onDuplicate = { title ->
                        vm.savePreset(
                            preset.copy(
                                id = "",
                                title = "$title $duplicateSuffix",
                                isBuiltIn = false,
                                titleRes = 0,
                                descriptionRes = 0
                            )
                        )
                    },
                    onEdit = { editing = preset },
                    onDelete = { deleting = preset }
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { editing = Preset("", "", "", emptyList(), emptyList(), null, isBuiltIn = false) },
            icon = { Icon(Icons.Outlined.Add, null) },
            text = { Text(stringResource(R.string.presets_new)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = ScreenPadding, bottom = contentPadding.calculateBottomPadding() + 16.dp)
        )
    }

    editing?.let { preset ->
        PresetEditorDialog(
            initial = preset,
            onSave = { saved ->
                vm.savePreset(saved)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.preset_delete_title)) },
            text = { Text(stringResource(R.string.preset_delete_text, preset.title)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePreset(preset.id)
                    deleting = null
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    pendingImport?.let { parsed ->
        ImportConfirmDialog(presets = parsed, onConfirm = vm::confirmImport, onDismiss = vm::cancelImport)
    }
}

@Composable
private fun PresetCard(
    preset: Preset,
    selected: Boolean,
    locked: Boolean,
    onSelect: () -> Unit,
    onDuplicate: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by rememberSaveable(preset.id) { mutableStateOf(false) }
    val title = presetTitle(preset)
    val description = presetDescription(preset)
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onSelect,
        enabled = !locked || selected,
        shape = MaterialTheme.shapes.large,
        color = if (selected) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.surfaceContainer,
        border = if (selected) BorderStroke(1.5.dp, scheme.primary) else null,
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                RadioButton(selected = selected, onClick = null, enabled = !locked || selected, modifier = Modifier.padding(12.dp))
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        Badge(if (preset.isBuiltIn) R.string.preset_builtin else R.string.preset_custom, preset.isBuiltIn)
                    }
                    if (description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 12.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.preset_on_commands), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                    CodeBlock(preset.onCommands.joinToString("\n"))
                    Text(stringResource(R.string.preset_off_commands), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                    CodeBlock(preset.offCommands.joinToString("\n"))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (expanded) R.string.preset_hide_commands else R.string.preset_show_commands))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onDuplicate(title) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.action_duplicate))
                }
                if (!preset.isBuiltIn) {
                    IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit)) }
                    IconButton(onClick = onDelete, enabled = !(locked && selected)) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(textRes: Int, builtIn: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (builtIn) scheme.secondaryContainer else scheme.tertiaryContainer,
        contentColor = if (builtIn) scheme.onSecondaryContainer else scheme.onTertiaryContainer
    ) {
        Text(
            stringResource(textRes),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
