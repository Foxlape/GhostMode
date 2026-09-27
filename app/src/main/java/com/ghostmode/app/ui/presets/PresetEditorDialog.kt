package com.ghostmode.app.ui.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ghostmode.app.R
import com.ghostmode.app.data.Preset
import com.ghostmode.app.ui.components.CodeBlock
import com.ghostmode.app.ui.theme.MonoStyle

private const val SEPARATOR = "\u0000"

/** Saves a [Preset] across configuration changes without making it Parcelable. */
val PresetSaver: Saver<Preset?, String> = Saver(
    save = { preset ->
        preset?.let {
            listOf(
                it.id, it.title, it.description,
                it.onCommands.joinToString("\n"), it.offCommands.joinToString("\n"),
                it.networkMaskCaptureCommand.orEmpty(), it.isBuiltIn.toString(),
                it.titleRes.toString(), it.descriptionRes.toString()
            ).joinToString(SEPARATOR)
        } ?: ""
    },
    restore = { raw ->
        if (raw.isEmpty()) null else raw.split(SEPARATOR).let { parts ->
            Preset(
                id = parts[0],
                title = parts[1],
                description = parts[2],
                onCommands = parts[3].lines().filter { it.isNotBlank() },
                offCommands = parts[4].lines().filter { it.isNotBlank() },
                networkMaskCaptureCommand = parts[5].ifEmpty { null },
                isBuiltIn = parts[6].toBoolean(),
                titleRes = parts[7].toInt(),
                descriptionRes = parts[8].toInt()
            )
        }
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetEditorDialog(initial: Preset, onSave: (Preset) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var description by rememberSaveable { mutableStateOf(initial.description) }
    var onText by rememberSaveable { mutableStateOf(initial.onCommands.joinToString("\n")) }
    var offText by rememberSaveable { mutableStateOf(initial.offCommands.joinToString("\n")) }
    var capture by rememberSaveable { mutableStateOf(initial.networkMaskCaptureCommand.orEmpty()) }

    val onCommands = onText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val offCommands = offText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val canSave = title.isNotBlank() && onCommands.isNotEmpty() && offCommands.isNotEmpty()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(if (initial.id.isEmpty()) R.string.editor_title_new else R.string.editor_title_edit)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.action_cancel)) }
                    },
                    actions = {
                        TextButton(
                            enabled = canSave,
                            onClick = {
                                onSave(
                                    initial.copy(
                                        title = title.trim(),
                                        description = description.trim(),
                                        onCommands = onCommands,
                                        offCommands = offCommands,
                                        networkMaskCaptureCommand = capture.trim().ifEmpty { null },
                                        isBuiltIn = false,
                                        titleRes = 0,
                                        descriptionRes = 0
                                    )
                                )
                            }
                        ) { Text(stringResource(R.string.action_save)) }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.editor_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.editor_field_description)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = onText,
                    onValueChange = { onText = it },
                    label = { Text(stringResource(R.string.editor_field_on)) },
                    supportingText = { Text(stringResource(R.string.editor_one_per_line)) },
                    textStyle = MonoStyle,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                )
                OutlinedTextField(
                    value = offText,
                    onValueChange = { offText = it },
                    label = { Text(stringResource(R.string.editor_field_off)) },
                    supportingText = { Text(stringResource(R.string.editor_one_per_line)) },
                    textStyle = MonoStyle,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                )
                OutlinedTextField(
                    value = capture,
                    onValueChange = { capture = it },
                    label = { Text(stringResource(R.string.editor_field_capture)) },
                    supportingText = { Text(stringResource(R.string.editor_capture_hint)) },
                    singleLine = true,
                    textStyle = MonoStyle.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.editor_placeholders_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.editor_placeholders_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Shows exactly which commands an imported file contains before anything is saved. */
@Composable
fun ImportConfirmDialog(presets: List<Preset>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.WarningAmber, null) },
        title = { Text(pluralStringResource(R.plurals.import_title, presets.size, presets.size)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.import_warning), style = MaterialTheme.typography.bodyMedium)
                presets.forEach { preset ->
                    Text(preset.title, style = MaterialTheme.typography.titleSmall)
                    CodeBlock(
                        (preset.onCommands.map { "ON  $it" } + preset.offCommands.map { "OFF $it" }).joinToString("\n")
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.action_import)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
