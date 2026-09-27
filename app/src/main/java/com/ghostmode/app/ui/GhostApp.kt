package com.ghostmode.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostmode.app.R
import com.ghostmode.app.ui.home.HomeScreen
import com.ghostmode.app.ui.presets.PresetsScreen
import com.ghostmode.app.ui.settings.SettingsScreen
import com.ghostmode.app.ui.tools.AboutScreen
import com.ghostmode.app.ui.tools.DiagnosticsScreen
import com.ghostmode.app.ui.tools.LogScreen
import com.ghostmode.app.ui.tools.StatsScreen

enum class Tab(val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    PRESETS(R.string.nav_presets, Icons.Outlined.Layers, Icons.Filled.Layers),
    SETTINGS(R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings)
}

enum class Page { DIAGNOSTICS, LOG, STATS, ABOUT }

private enum class Sheet { SCHEDULE, SIM }

/** Callbacks that need the Activity (permissions, file pickers, external links). */
data class AppHost(
    val onNotificationToggle: (Boolean) -> Unit,
    val onAddTile: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onImport: () -> Unit,
    val onExport: () -> Unit
)

@Composable
fun GhostApp(vm: MainViewModel, host: AppHost, initialTab: Tab = Tab.HOME, initialPages: List<Page> = emptyList()) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var pages by rememberSaveable(stateSaver = pageStackSaver) { mutableStateOf(initialPages) }
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    fun push(page: Page) {
        pages = pages + page
    }

    fun pop() {
        pages = pages.dropLast(1)
    }

    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            val result = snackbar.showSnackbar(
                message = context.getString(message.text),
                actionLabel = if (message.showLogAction) context.getString(R.string.action_open_log) else null,
                duration = if (message.showLogAction) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) push(Page.LOG)
        }
    }

    BackHandler(enabled = pages.isNotEmpty() || tab != Tab.HOME) {
        if (pages.isNotEmpty()) pop() else tab = Tab.HOME
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = pages.lastOrNull(),
            transitionSpec = {
                val forward = (targetState?.ordinal ?: -1) >= (initialState?.ordinal ?: -1) && targetState != null
                if (forward) {
                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut()
                } else {
                    fadeIn() togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                }
            },
            label = "pages"
        ) { page ->
            when (page) {
                null -> MainTabs(
                    vm = vm,
                    host = host,
                    tab = tab,
                    onTabChange = { tab = it },
                    onOpen = ::push,
                    onSheet = { sheet = it },
                    snackbar = snackbar
                )
                Page.DIAGNOSTICS -> DiagnosticsScreen(vm, onBack = ::pop, onOpenLog = { push(Page.LOG) })
                Page.LOG -> LogScreen(vm, onBack = ::pop)
                Page.STATS -> StatsScreen(vm, onBack = ::pop)
                Page.ABOUT -> AboutScreen(onBack = ::pop, onOpenUrl = host.onOpenUrl)
            }
        }
        if (pages.isNotEmpty()) {
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }

    when (sheet) {
        Sheet.SCHEDULE -> {
            val enabled by vm.scheduleEnabled.collectAsStateWithLifecycle()
            val start by vm.scheduleStart.collectAsStateWithLifecycle()
            val end by vm.scheduleEnd.collectAsStateWithLifecycle()
            ScheduleSheet(enabled, start, end, onApply = vm::setSchedule, onDismiss = { sheet = null })
        }
        Sheet.SIM -> {
            val mode by vm.simSlotMode.collectAsStateWithLifecycle()
            val isOn by vm.isOn.collectAsStateWithLifecycle()
            SimSheet(current = mode, locked = isOn, onSelect = vm::setSimSlotMode, onDismiss = { sheet = null })
        }
        null -> Unit
    }
}

@Composable
private fun MainTabs(
    vm: MainViewModel,
    host: AppHost,
    tab: Tab,
    onTabChange: (Tab) -> Unit,
    onOpen: (Page) -> Unit,
    onSheet: (Sheet) -> Unit,
    snackbar: SnackbarHostState
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { onTabChange(item) },
                        icon = { Icon(if (item == tab) item.selectedIcon else item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tabs") { current ->
            when (current) {
                Tab.HOME -> HomeScreen(
                    vm = vm,
                    contentPadding = padding,
                    onOpenPresets = { onTabChange(Tab.PRESETS) },
                    onOpenDiagnostics = { onOpen(Page.DIAGNOSTICS) },
                    onOpenStats = { onOpen(Page.STATS) },
                    onEditSchedule = { onSheet(Sheet.SCHEDULE) },
                    onEditSim = { onSheet(Sheet.SIM) },
                    onOpenUrl = host.onOpenUrl
                )
                Tab.PRESETS -> PresetsScreen(vm, padding, onImport = host.onImport, onExport = host.onExport)
                Tab.SETTINGS -> SettingsScreen(
                    vm = vm,
                    contentPadding = padding,
                    onNotificationToggle = host.onNotificationToggle,
                    onEditSchedule = { onSheet(Sheet.SCHEDULE) },
                    onAddTile = host.onAddTile,
                    onOpenDiagnostics = { onOpen(Page.DIAGNOSTICS) },
                    onOpenLog = { onOpen(Page.LOG) },
                    onOpenStats = { onOpen(Page.STATS) },
                    onOpenAbout = { onOpen(Page.ABOUT) }
                )
            }
        }
    }
}

private val pageStackSaver = listSaver<List<Page>, String>(
    save = { stack -> stack.map { it.name } },
    restore = { names -> names.map { Page.valueOf(it) } }
)
