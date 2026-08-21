package com.ghostmode.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ghostmode.app.data.GhostStateRepository
import com.ghostmode.app.data.ThemeMode
import com.ghostmode.app.scheduling.ScheduleManager
import com.ghostmode.app.service.StatusNotificationManager
import com.ghostmode.app.support.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import java.util.Locale

class GhostModeApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val stateRepository: GhostStateRepository by lazy { GhostStateRepository.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        applyStoredTheme()
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            val systemLanguage = Locale.getDefault().language
            if (systemLanguage.startsWith("ru")) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru"))
            }
        }
        ScheduleManager.update(this)
        UpdateManager.refresh()
        combine(
            stateRepository.isOn,
            stateRepository.notificationEnabled,
            stateRepository.isOnTimestampMs
        ) { isOn, notificationsEnabled, timestampMs ->
            StatusNotificationManager.update(this, isOn, notificationsEnabled, timestampMs)
        }
            .launchIn(applicationScope)
    }

    private fun applyStoredTheme() {
        AppCompatDelegate.setDefaultNightMode(
            stateRepository.themeMode.value.toAppCompatNightMode()
        )
    }
}
