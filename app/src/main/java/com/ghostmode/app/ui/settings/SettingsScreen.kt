package com.ghostmode.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Troubleshoot
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostmode.app.R
import com.ghostmode.app.data.ThemeMode
import com.ghostmode.app.support.UpdateState
import com.ghostmode.app.ui.Format
import com.ghostmode.app.ui.MainViewModel
import com.ghostmode.app.ui.components.ScreenPadding
import com.ghostmode.app.ui.components.SectionHeader
import com.ghostmode.app.ui.components.SettingsGroup
import com.ghostmode.app.ui.components.SettingsRow
import com.ghostmode.app.ui.components.SwitchRow
import com.ghostmode.app.ui.home.requestIgnoreBatteryOptimizations
import com.ghostmode.app.ui.home.startActivitySafely

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onNotificationToggle: (Boolean) -> Unit,
    onEditSchedule: () -> Unit,
    onAddTile: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val context = LocalContext.current
    val notificationEnabled by vm.notificationEnabled.collectAsStateWithLifecycle()
    val scheduleEnabled by vm.scheduleEnabled.collectAsStateWithLifecycle()
    val scheduleStart by vm.scheduleStart.collectAsStateWithLifecycle()
    val scheduleEnd by vm.scheduleEnd.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle()
    val updateCheck by vm.updateCheckEnabled.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val systemStatus by vm.systemStatus.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        SectionHeader(stringResource(R.string.settings_section_mode))
        SettingsGroup {
            SwitchRow(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.settings_notification),
                summary = stringResource(R.string.settings_notification_summary),
                checked = notificationEnabled,
                onCheckedChange = onNotificationToggle
            )
            Divider()
            SettingsRow(
                icon = Icons.Outlined.Schedule,
                title = stringResource(R.string.schedule_title),
                summary = if (scheduleEnabled) {
                    stringResource(
                        R.string.settings_schedule_on,
                        Format.minuteOfDay(context, scheduleStart),
                        Format.minuteOfDay(context, scheduleEnd)
                    )
                } else {
                    stringResource(R.string.value_off)
                },
                onClick = onEditSchedule
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Divider()
                SettingsRow(
                    icon = Icons.Outlined.Alarm,
                    title = stringResource(R.string.settings_exact_alarms),
                    summary = stringResource(if (systemStatus.canScheduleExact) R.string.value_allowed else R.string.settings_exact_alarms_denied),
                    onClick = {
                        context.startActivitySafely(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        )
                    }
                )
            }
            Divider()
            SettingsRow(
                icon = Icons.Outlined.BatterySaver,
                title = stringResource(R.string.settings_battery),
                summary = stringResource(if (systemStatus.isBatteryExempt) R.string.value_unrestricted else R.string.settings_battery_restricted),
                onClick = { context.requestIgnoreBatteryOptimizations() }
            )
        }

        SectionHeader(stringResource(R.string.settings_section_quick))
        SettingsGroup {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.ViewQuilt,
                    title = stringResource(R.string.settings_add_tile),
                    summary = stringResource(R.string.settings_add_tile_summary),
                    onClick = onAddTile
                )
                Divider()
            }
            SettingsRow(
                icon = Icons.Outlined.Widgets,
                title = stringResource(R.string.settings_widget),
                summary = stringResource(R.string.settings_widget_summary)
            )
        }

        SectionHeader(stringResource(R.string.settings_section_appearance))
        SettingsGroup {
            SettingsRow(
                icon = Icons.Outlined.DarkMode,
                title = stringResource(R.string.settings_theme),
                summary = stringResource(themeLabel(themeMode)),
                onClick = { dialog = DIALOG_THEME }
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Divider()
                SwitchRow(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    checked = dynamicColor,
                    onCheckedChange = vm::setDynamicColor
                )
            }
            Divider()
            SettingsRow(
                icon = Icons.Outlined.Language,
                title = stringResource(R.string.settings_language),
                summary = stringResource(languageLabel(currentLanguageTag())),
                onClick = { dialog = DIALOG_LANGUAGE }
            )
        }

        SectionHeader(stringResource(R.string.settings_section_updates))
        SettingsGroup {
            SwitchRow(
                icon = Icons.Outlined.Update,
                title = stringResource(R.string.settings_update_auto),
                summary = stringResource(R.string.settings_update_auto_summary),
                checked = updateCheck,
                onCheckedChange = vm::setUpdateCheckEnabled
            )
            Divider()
            SettingsRow(
                icon = Icons.Outlined.SystemUpdate,
                title = stringResource(R.string.settings_update_now),
                summary = when (val state = updateState) {
                    UpdateState.Idle -> stringResource(R.string.update_idle)
                    UpdateState.Checking -> stringResource(R.string.update_checking)
                    UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
                    UpdateState.Failed -> stringResource(R.string.update_failed)
                    is UpdateState.Available -> stringResource(R.string.alert_update_title, state.release.versionName)
                },
                onClick = vm::checkForUpdates
            )
        }

        SectionHeader(stringResource(R.string.settings_section_tools))
        SettingsGroup {
            SettingsRow(Icons.Outlined.Troubleshoot, stringResource(R.string.diagnostics_title), stringResource(R.string.settings_diagnostics_summary), onOpenDiagnostics)
            Divider()
            SettingsRow(Icons.Outlined.Terminal, stringResource(R.string.log_title), stringResource(R.string.settings_log_summary), onOpenLog)
            Divider()
            SettingsRow(Icons.Outlined.BarChart, stringResource(R.string.stats_title), stringResource(R.string.settings_stats_summary), onOpenStats)
        }

        SectionHeader(stringResource(R.string.settings_section_about))
        SettingsGroup {
            SettingsRow(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.about_title),
                summary = stringResource(R.string.about_version, com.ghostmode.app.BuildConfig.VERSION_NAME),
                onClick = onOpenAbout
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    when (dialog) {
        DIALOG_THEME -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { it to stringResource(themeLabel(it)) },
            selected = themeMode,
            onSelect = {
                vm.setThemeMode(it)
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        DIALOG_LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = LANGUAGES.map { it to stringResource(languageLabel(it)) },
            selected = currentLanguageTag(),
            onSelect = { tag ->
                dialog = null
                AppCompatDelegate.setApplicationLocales(
                    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
                )
            },
            onDismiss = { dialog = null }
        )
    }
}

@Composable
private fun Divider() = HorizontalDivider(
    modifier = Modifier.padding(start = 66.dp),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
)

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Spacer(Modifier.width(14.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.LIGHT -> R.string.theme_light
}

private val LANGUAGES = listOf("", "en", "ru")

private fun currentLanguageTag(): String =
    AppCompatDelegate.getApplicationLocales().get(0)?.language.orEmpty()

private fun languageLabel(tag: String): Int = when (tag) {
    "en" -> R.string.language_english
    "ru" -> R.string.language_russian
    else -> R.string.language_system
}

private const val DIALOG_THEME = "theme"
private const val DIALOG_LANGUAGE = "language"
