package com.ghostmode.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ghostmode.app.R
import com.ghostmode.app.data.CommandLogEntry

private const val SUCCESS_EXIT_CODE = 0
private const val OUTPUT_COPY_SEPARATOR = "\n---\n"
private val CARD_CONTENT_PADDING = 12.dp
private val CARD_ITEM_SPACING = 8.dp

@Composable
fun LogPanel(
    entries: List<CommandLogEntry>,
    onClear: () -> Unit,
    onRemoveEntry: (CommandLogEntry) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(CARD_ITEM_SPACING)) {
        LogPanelHeader(entryCount = entries.size, onClear = onClear)
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.log_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            entries.forEach { entry ->
                LogEntryCard(entry = entry, onRemoveEntry = onRemoveEntry)
            }
        }
    }
}

@Composable
private fun LogPanelHeader(entryCount: Int, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.log_title),
            style = MaterialTheme.typography.titleMedium
        )
        TextButton(
            onClick = onClear,
            enabled = entryCount > 0
        ) {
            Text(text = stringResource(R.string.action_clear_log))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogEntryCard(entry: CommandLogEntry, onRemoveEntry: (CommandLogEntry) -> Unit) {
    var isExpanded by remember(entry.timestampMs) { mutableStateOf(false) }
    var isMenuOpen by remember(entry.timestampMs) { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { isExpanded = !isExpanded },
                    onLongClick = { isMenuOpen = true }
                )
        ) {
            Column(
                modifier = Modifier.padding(CARD_CONTENT_PADDING),
                verticalArrangement = Arrangement.spacedBy(CARD_ITEM_SPACING)
            ) {
                Text(
                    text = entry.command,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
                ExitCodeChip(
                    exitCode = entry.exitCode,
                    onClick = { isExpanded = !isExpanded }
                )
                if (isExpanded) {
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(CARD_ITEM_SPACING)) {
                        OutputText(text = entry.stdout)
                        OutputText(text = entry.stderr)
                    }
                }
            }
        }
        DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.log_action_copy_command)) },
                onClick = {
                    isMenuOpen = false
                    clipboardManager.setText(AnnotatedString(entry.command))
                }
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.log_action_copy_output)) },
                onClick = {
                    isMenuOpen = false
                    clipboardManager.setText(AnnotatedString(entry.copyableOutput()))
                }
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.log_action_delete_entry)) },
                onClick = {
                    isMenuOpen = false
                    onRemoveEntry(entry)
                }
            )
        }
    }
}

private fun CommandLogEntry.copyableOutput(): String =
    listOf(stdout, stderr)
        .filter { part -> part.isNotEmpty() }
        .joinToString(separator = OUTPUT_COPY_SEPARATOR)

@Composable
private fun ExitCodeChip(exitCode: Int, onClick: () -> Unit) {
    val labelColor = if (exitCode == SUCCESS_EXIT_CODE) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    AssistChip(
        onClick = onClick,
        label = { Text(text = stringResource(R.string.log_exit_code, exitCode)) },
        colors = AssistChipDefaults.assistChipColors(labelColor = labelColor)
    )
}

@Composable
private fun OutputText(text: String) {
    if (text.isNotEmpty()) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
