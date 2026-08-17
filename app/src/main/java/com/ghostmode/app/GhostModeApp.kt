package com.ghostmode.app

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ghostmode.app.data.GhostStateRepository
import com.ghostmode.app.scheduling.ScheduleManager
import com.ghostmode.app.service.GhostStatusService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class GhostModeApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val stateRepository: GhostStateRepository by lazy { GhostStateRepository(this) }

    override fun onCreate() {
        super.onCreate()
        ScheduleManager.update(this)
        combine(stateRepository.isOn, stateRepository.notificationEnabled) { isOn, notificationsEnabled ->
            isOn && notificationsEnabled
        }
            .onEach { shouldRunService -> startOrStopStatusService(shouldRunService) }
            .launchIn(applicationScope)
    }

    private fun startOrStopStatusService(shouldRun: Boolean) {
        val statusServiceIntent = Intent(this, GhostStatusService::class.java)
        try {
            if (shouldRun) {
                ContextCompat.startForegroundService(this, statusServiceIntent)
            } else {
                stopService(statusServiceIntent)
            }
        } catch (_: IllegalStateException) {
        }
    }
}
