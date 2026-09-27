package com.ghostmode.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ghostmode.app.appGraph

/**
 * Handles schedule boundaries plus system events that invalidate alarms or the applied state
 * (boot, clock / time zone change, app update, exact-alarm permission change).
 */
class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_TICK && action !in SYSTEM_ACTIONS) return
        val graph = context.appGraph
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        graph.actions.launch {
            try {
                if (action == Intent.ACTION_BOOT_COMPLETED && controller.needsReapply()) {
                    reapply()
                }
                applySchedule(graph.state.scheduleEnabled.value, isBoundary = action == ACTION_TICK, context = appContext)
            } catch (error: Exception) {
                Log.e(TAG, "Handling $action failed", error)
            } finally {
                ScheduleManager.update(appContext)
                refreshSurfaces()
                pendingResult.finish()
            }
        }
    }

    /**
     * At a boundary the mode follows the window in both directions. On other events it is only
     * turned on (catching up a start missed while the phone was off) — never forced off, so a
     * mode turned on manually survives a reboot.
     */
    private suspend fun com.ghostmode.app.system.GhostActions.applySchedule(
        enabled: Boolean,
        isBoundary: Boolean,
        context: Context
    ) {
        if (!enabled) return
        val state = context.appGraph.state
        val inWindow = ScheduleWindow.contains(
            ScheduleWindow.currentMinuteOfDay(),
            state.scheduleStartMinuteOfDay.value,
            state.scheduleEndMinuteOfDay.value
        )
        val isOn = state.isOn.value
        when {
            inWindow && !isOn -> turnOn()
            !inWindow && isOn && isBoundary -> turnOff()
        }
    }

    companion object {
        private const val TAG = "GhostSchedule"
        const val ACTION_TICK = "com.ghostmode.app.schedule.TICK"

        private val SYSTEM_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31); only ever delivered on 31+.
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
    }
}
