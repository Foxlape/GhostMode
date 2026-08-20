package com.ghostmode.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ghostmode.app.data.GhostStateRepository
import com.ghostmode.app.data.PresetRepository
import com.ghostmode.app.domain.GhostModeController
import com.ghostmode.app.shell.AutoShellExecutor
import com.ghostmode.app.shell.RootShellExecutor
import com.ghostmode.app.shell.ShizukuManager
import com.ghostmode.app.widget.GhostWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TURN_OFF) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            val stateRepository = GhostStateRepository(appContext)
            val shizukuManager = ShizukuManager(appContext)
            try {
                shizukuManager.start()
                val rootExecutor = RootShellExecutor()
                val ghostModeController = GhostModeController(
                    AutoShellExecutor(rootExecutor, shizukuManager),
                    PresetRepository(appContext),
                    stateRepository
                )
                rootExecutor.probeRoot()
                if (stateRepository.isOn.value) {
                    ghostModeController.turnOff()
                }
            } catch (_: Exception) {
            } finally {
                StatusNotificationManager.update(
                    appContext,
                    isOn = false,
                    notificationEnabled = stateRepository.notificationEnabled.value,
                    timestampMs = 0L
                )
                GhostWidgetProvider.refreshAll(appContext, false)
                shizukuManager.stop()
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_TURN_OFF = "com.ghostmode.app.notification.TURN_OFF"
    }
}
