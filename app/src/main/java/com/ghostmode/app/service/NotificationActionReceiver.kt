package com.ghostmode.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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
            val stateRepository = GhostStateRepository.getInstance(appContext)
            val shizukuManager = ShizukuManager(appContext)
            try {
                shizukuManager.start()
                val rootExecutor = RootShellExecutor()
                val ghostModeController = GhostModeController(
                    AutoShellExecutor(rootExecutor, shizukuManager),
                    PresetRepository.getInstance(appContext),
                    stateRepository
                )
                rootExecutor.probeRoot()
                if (stateRepository.isOn.value) {
                    ghostModeController.turnOff()
                }
            } catch (error: Exception) {
                Log.e(TAG, "Scheduled turn-off from notification failed", error)
            } finally {
                StatusNotificationManager.update(
                    appContext,
                    isOn = stateRepository.isOn.value,
                    notificationEnabled = stateRepository.notificationEnabled.value,
                    timestampMs = if (stateRepository.isOn.value) stateRepository.isOnTimestampMs.value else 0L
                )
                GhostWidgetProvider.refreshAll(appContext, false)
                shizukuManager.stop()
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "GhostNotifAction"

        const val ACTION_TURN_OFF = "com.ghostmode.app.notification.TURN_OFF"
    }
}
