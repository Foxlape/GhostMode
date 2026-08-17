package com.ghostmode.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ghostmode.app.R
import com.ghostmode.app.data.Preset

private const val COMMAND_SEPARATOR = "\n"
private val FIELD_SPACING = 8.dp

@Composable
fun PresetEditorDialog(
    initialPreset: Preset,
    isNewPreset: Boolean,
    onSave: (Preset) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(initialPreset.title) }
    var description by rememberSaveable { mutableStateOf(initialPreset.description) }
    var onCommandsText by rememberSaveable { mutableStateOf(initialPreset.onCommands.joinToString(COMMAND_SEPARATOR)) }
    var offCommandsText by rememberSaveable { mutableStateOf(initialPreset.offCommands.joinToString(COMMAND_SEPARATOR)) }
    var captureCommand by rememberSaveable { mutableStateOf(initialPreset.networkMaskCaptureCommand.orEmpty()) }
    val parsedOnCommands = parseCommands(onCommandsText)
    val parsedOffCommands = parseCommands(offCommandsText)
    val isSaveEnabled = title.isNotBlank() &&
        parsedOnCommands.isNotEmpty() &&
        parsedOffCommands.isNotEmpty()
    val savedPreset = buildPreset(
        initialPreset = initialPreset,
        title = title,
        description = description,
        onCommands = parsedOnCommands,
        offCommands = parsedOffCommands,
        captureCommand = captureCommand
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = editorDialogTitle(isNewPreset)) },
        text = {
            PresetEditorFields(
                title = title,
                onTitleChange = { title = it },
                description = description,
                onDescriptionChange = { description = it },
                onCommandsText = onCommandsText,
                onOnCommandsChange = { onCommandsText = it },
                offCommandsText = offCommandsText,
                onOffCommandsChange = { offCommandsText = it },
                captureCommand = captureCommand,
                onCaptureCommandChange = { captureCommand = it }
            )
        },
        confirmButton = {
            TextButton(
                enabled = isSaveEnabled,
                onClick = { onSave(savedPreset) }
            ) {
                Text(text = stringResource(R.string.editor_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.editor_cancel))
            }
        }
    )
}

@Composable
private fun PresetEditorFields(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    onCommandsText: String,
    onOnCommandsChange: (String) -> Unit,
    offCommandsText: String,
    onOffCommandsChange: (String) -> Unit,
    captureCommand: String,
    onCaptureCommandChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(FIELD_SPACING)
    ) {
        PresetInfoFields(
            title = title,
            onTitleChange = onTitleChange,
            description = description,
            onDescriptionChange = onDescriptionChange
        )
        PresetCommandFields(
            onCommandsText = onCommandsText,
            onOnCommandsChange = onOnCommandsChange,
            offCommandsText = offCommandsText,
            onOffCommandsChange = onOffCommandsChange,
            captureCommand = captureCommand,
            onCaptureCommandChange = onCaptureCommandChange
        )
    }
}

@Composable
private fun PresetInfoFields(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(FIELD_SPACING)) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(text = stringResource(R.string.editor_hint_title)) }
        )
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(text = stringResource(R.string.editor_hint_description)) }
        )
    }
}

@Composable
private fun PresetCommandFields(
    onCommandsText: String,
    onOnCommandsChange: (String) -> Unit,
    offCommandsText: String,
    onOffCommandsChange: (String) -> Unit,
    captureCommand: String,
    onCaptureCommandChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(FIELD_SPACING)) {
        OutlinedTextField(
            value = onCommandsText,
            onValueChange = onOnCommandsChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.editor_hint_on_commands)) }
        )
        OutlinedTextField(
            value = offCommandsText,
            onValueChange = onOffCommandsChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.editor_hint_off_commands)) }
        )
        OutlinedTextField(
            value = captureCommand,
            onValueChange = onCaptureCommandChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(text = stringResource(R.string.editor_hint_capture)) }
        )
    }
}

@Composable
private fun editorDialogTitle(isNewPreset: Boolean): String =
    stringResource(
        if (isNewPreset) R.string.editor_title_new else R.string.editor_title_edit
    )

private fun parseCommands(commandsText: String): List<String> =
    commandsText.split(COMMAND_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotEmpty() }

private fun buildPreset(
    initialPreset: Preset,
    title: String,
    description: String,
    onCommands: List<String>,
    offCommands: List<String>,
    captureCommand: String
): Preset = Preset(
    id = initialPreset.id,
    title = title.trim(),
    description = description.trim(),
    onCommands = onCommands,
    offCommands = offCommands,
    networkMaskCaptureCommand = captureCommand.trim().ifEmpty { null },
    isBuiltIn = false
)
