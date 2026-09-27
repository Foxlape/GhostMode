package com.ghostmode.app.ui.tools

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostmode.app.R
import com.ghostmode.app.data.CommandLogEntry
import com.ghostmode.app.domain.GhostModeController
import com.ghostmode.app.ui.Format
import com.ghostmode.app.ui.MainViewModel
import com.ghostmode.app.ui.components.CodeBlock
import com.ghostmode.app.ui.components.ScreenPadding
import com.ghostmode.app.ui.components.SubScreenTopBar
import com.ghostmode.app.ui.theme.LocalGhostColors
import com.ghostmode.app.ui.theme.MonoStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(vm: MainViewModel, onBack: () -> Unit) {
    val entries by vm.logEntries.collectAsStateWithLifecycle()
    val copy = com.ghostmode.app.ui.components.rememberCopyToClipboard()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SubScreenTopBar(stringResource(R.string.log_title), onBack, scrollBehavior) {
                IconButton(onClick = { copy(vm.logAsText()) }, enabled = entries.isNotEmpty()) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.log_copy_all))
                }
                IconButton(onClick = vm::clearLog, enabled = entries.isNotEmpty()) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.log_clear))
                }
            }
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Terminal, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.log_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries.asReversed()) { entry ->
                    LogEntry(entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntry(entry: CommandLogEntry) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val colors = LocalGhostColors.current
    val statusColor = when {
        entry.exitCode == GhostModeController.EXIT_NOTE -> MaterialTheme.colorScheme.outline
        entry.isSuccess -> colors.success
        else -> MaterialTheme.colorScheme.error
    }
    val hasOutput = entry.stdout.isNotBlank() || entry.stderr.isNotBlank()
    Surface(
        onClick = { expanded = !expanded },
        enabled = hasOutput,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(10.dp))
                Text(
                    entry.command,
                    style = MonoStyle,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(Modifier.padding(start = 18.dp, top = 6.dp)) {
                Text(
                    Format.clock(context, entry.timestampMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (entry.exitCode == GhostModeController.EXIT_NOTE) stringResource(R.string.log_note)
                    else stringResource(R.string.log_exit_code, entry.exitCode),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
            }
            if (expanded && hasOutput) {
                Spacer(Modifier.size(10.dp))
                CodeBlock(listOf(entry.stdout.trimEnd(), entry.stderr.trimEnd()).filter { it.isNotEmpty() }.joinToString("\n"))
            }
        }
    }
}
