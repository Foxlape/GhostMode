package com.ghostmode.app

import android.app.Application
import com.ghostmode.app.data.GhostStateRepository
import com.ghostmode.app.scheduling.ScheduleManager
import com.ghostmode.app.service.StatusNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn

class GhostModeApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val stateRepository: GhostStateRepository by lazy { GhostStateRepository(this) }

    override fun onCreate() {
        super.onCreate()
        ScheduleManager.update(this)
        combine(
            stateRepository.isOn,
            stateRepository.notificationEnabled,
            stateRepository.isOnTimestampMs
        ) { isOn, notificationsEnabled, timestampMs ->
            StatusNotificationManager.update(this, isOn, notificationsEnabled, timestampMs)
        }
            .launchIn(applicationScope)
    }
}
