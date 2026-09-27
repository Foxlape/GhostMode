package com.ghostmode.app.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostmode.app.R
import com.ghostmode.app.data.GhostSession
import com.ghostmode.app.data.SessionStats
import com.ghostmode.app.ui.Format
import com.ghostmode.app.ui.MainViewModel
import com.ghostmode.app.ui.components.GhostCard
import com.ghostmode.app.ui.components.ScreenPadding
import com.ghostmode.app.ui.components.SectionHeader
import com.ghostmode.app.ui.components.SubScreenTopBar
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val daily = SessionStats.dailyTotals(sessions, now)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { SubScreenTopBar(stringResource(R.string.stats_title), onBack, scrollBehavior) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stringResource(R.string.stats_today), Format.duration(context, daily.last()), Modifier.weight(1f))
                StatTile(stringResource(R.string.stats_week), Format.duration(context, daily.sum()), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    stringResource(R.string.stats_all),
                    Format.duration(context, SessionStats.durationInWindow(sessions, 0L, now)),
                    Modifier.weight(1f)
                )
                StatTile(stringResource(R.string.stats_sessions), sessions.size.toString(), Modifier.weight(1f))
            }

            SectionHeader(stringResource(R.string.stats_last_7_days))
            GhostCard { WeekChart(daily, now) }

            if (sessions.isNotEmpty()) {
                SectionHeader(stringResource(R.string.stats_recent))
                GhostCard {
                    sessions.takeLast(RECENT_LIMIT).asReversed().forEachIndexed { index, session ->
                        if (index > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        SessionRow(session, now)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    GhostCard(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun WeekChart(daily: List<Long>, now: Long) {
    val context = LocalContext.current
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = daily.maxOrNull()?.coerceAtLeast(60 * 60_000L) ?: 1L
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val count = daily.size
        val gap = 12.dp.toPx()
        val barWidth = (size.width - gap * (count - 1)) / count
        daily.forEachIndexed { index, value ->
            val x = index * (barWidth + gap)
            drawRoundRect(trackColor, Offset(x, 0f), Size(barWidth, size.height), CornerRadius(10.dp.toPx()))
            val barHeight = size.height * (value.toFloat() / max)
            if (barHeight > 0f) {
                drawRoundRect(
                    barColor,
                    Offset(x, size.height - barHeight),
                    Size(barWidth, barHeight),
                    CornerRadius(10.dp.toPx())
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row {
        daily.indices.forEach { index ->
            Text(
                Format.weekdayShort(context, SessionStats.startOfDay(now, index - (daily.size - 1))),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SessionRow(session: GhostSession, now: Long) {
    val context = LocalContext.current
    val end = if (session.isOpen) now else session.endMs
    Row {
        Column(Modifier.weight(1f)) {
            Text(Format.date(context, session.startMs), style = MaterialTheme.typography.bodyLarge)
            Text(
                "${Format.clock(context, session.startMs)} – " +
                    if (session.isOpen) stringResource(R.string.stats_now) else Format.clock(context, session.endMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(Format.duration(context, end - session.startMs), style = MaterialTheme.typography.titleMedium)
    }
}

private const val RECENT_LIMIT = 15
