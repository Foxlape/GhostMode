package com.ghostmode.app.scheduling

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
import java.util.Calendar

class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val action = intent.action ?: return
        val isTick = action == ACTION_TICK
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED
        val isSystemReschedule = action in SYSTEM_RESCHEDULE_ACTIONS
        if (!isTick && !isSystemReschedule) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            val stateRepository = GhostStateRepository(appContext)
            val shizukuManager = ShizukuManager(appContext)
            try {
                if (isSystemReschedule) ScheduleManager.update(appContext)
                if (stateRepository.scheduleEnabled.value) {
                    shizukuManager.start()
                    val rootExecutor = RootShellExecutor()
                    val ghostModeController = GhostModeController(
                        AutoShellExecutor(rootExecutor, shizukuManager),
                        PresetRepository(appContext),
                        stateRepository
                    )
                    rootExecutor.probeRoot()
                    applyScheduledState(stateRepository, ghostModeController)
                } else if (isBoot) {
                    // On reboot without schedule enabled, modem state is reset by the OS.
                    // Reset Ghost Mode state to OFF so UI / QS Tile / Widget reflect actual modem state.
                    stateRepository.setIsOn(false)
                }
            } catch (_: IllegalStateException) {
            } finally {
                ScheduleManager.update(appContext)
                com.ghostmode.app.service.StatusNotificationManager.update(
                    appContext,
                    stateRepository.isOn.value,
                    stateRepository.notificationEnabled.value,
                    stateRepository.isOnTimestampMs.value
                )
                GhostWidgetProvider.refreshAll(appContext, stateRepository.isOn.value)
                shizukuManager.stop()
                pendingResult.finish()
            }
        }
    }

    private suspend fun applyScheduledState(
        stateRepository: GhostStateRepository,
        ghostModeController: GhostModeController
    ) {
        if (!stateRepository.scheduleEnabled.value) return
        val inWindow = isMinuteOfDayInWindow(currentMinuteOfDay(), stateRepository)
        val isOn = stateRepository.isOn.value
        if (inWindow && !isOn) {
            ghostModeController.turnOn()
        } else if (!inWindow && isOn) {
            ghostModeController.turnOff()
        }
    }

    private fun isMinuteOfDayInWindow(
        minuteOfDay: Int,
        stateRepository: GhostStateRepository
    ): Boolean {
        val startMinute = stateRepository.scheduleStartMinuteOfDay.value
        val endMinute = stateRepository.scheduleEndMinuteOfDay.value
        return if (startMinute <= endMinute) {
            minuteOfDay >= startMinute && minuteOfDay < endMinute
        } else {
            minuteOfDay >= startMinute || minuteOfDay < endMinute
        }
    }

    private fun currentMinuteOfDay(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.HOUR_OF_DAY) * MINUTES_PER_HOUR + calendar.get(Calendar.MINUTE)
    }

    companion object {
        const val ACTION_TICK = "com.ghostmode.app.schedule.TICK"

        private val SYSTEM_RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
        private const val MINUTES_PER_HOUR = 60
    }
}
