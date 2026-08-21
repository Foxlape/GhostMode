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

class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TIMER_FIRE) return
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
                if (stateRepository.isOn.value && stateRepository.timerFireAtMs.value > 0L) {
                    ghostModeController.turnOff()
                }
            } catch (error: Exception) {
                Log.e(TAG, "Timed turn-off failed", error)
            } finally {
                stateRepository.clearTimerFireAt()
                StatusNotificationManager.update(
                    appContext,
                    isOn = stateRepository.isOn.value,
                    notificationEnabled = stateRepository.notificationEnabled.value,
                    timestampMs = if (stateRepository.isOn.value) stateRepository.isOnTimestampMs.value else 0L
                )
                GhostWidgetProvider.refreshAll(appContext, stateRepository.isOn.value)
                shizukuManager.stop()
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "GhostTimer"

        const val ACTION_TIMER_FIRE = "com.ghostmode.app.timer.FIRE"
    }
}
