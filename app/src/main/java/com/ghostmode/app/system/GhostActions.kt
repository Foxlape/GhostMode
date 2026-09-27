package com.ghostmode.app.system

import android.content.Context
import com.ghostmode.app.data.GhostStateRepository
import com.ghostmode.app.domain.GhostModeController
import com.ghostmode.app.domain.TurnOutcome
import com.ghostmode.app.scheduling.TimerManager
import com.ghostmode.app.service.StatusNotificationManager
import com.ghostmode.app.tile.GhostTileService
import com.ghostmode.app.widget.GhostWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Entry point for every user- or system-triggered mode change (app, tile, widget, shortcuts,
 * schedule, timer, notification). Keeps alarms and system surfaces consistent with the result.
 */
class GhostActions(
    private val context: Context,
    val controller: GhostModeController,
    private val state: GhostStateRepository,
    private val scope: CoroutineScope
) {

    suspend fun turnOn(timerMinutes: Int? = null): TurnOutcome {
        val outcome = if (state.isOn.value) controller.reapply() else controller.turnOn()
        if (timerMinutes != null && state.isOn.value) armTimerAt(System.currentTimeMillis() + timerMinutes * MILLIS_PER_MINUTE)
        refreshSurfaces()
        return outcome
    }

    suspend fun turnOff(): TurnOutcome {
        val outcome = controller.turnOff()
        if (!state.isOn.value) TimerManager.cancel(context)
        refreshSurfaces()
        return outcome
    }

    suspend fun toggle(): TurnOutcome = if (state.isOn.value) turnOff() else turnOn()

    suspend fun reapply(): TurnOutcome {
        val outcome = controller.reapply()
        refreshSurfaces()
        return outcome
    }

    /** Fire-and-forget variant for callers without their own coroutine scope. */
    fun launch(block: suspend GhostActions.() -> Unit): Job = scope.launch { block() }

    fun armTimerAt(fireAtMs: Long) {
        state.setTimerFireAtMs(fireAtMs)
        TimerManager.schedule(context, fireAtMs)
    }

    fun cancelTimer() {
        TimerManager.cancel(context)
        state.clearTimer()
    }

    fun refreshSurfaces() {
        val isOn = state.isOn.value
        GhostWidgetProvider.refreshAll(context, isOn)
        GhostTileService.requestTileUpdate(context)
        StatusNotificationManager.update(
            context = context,
            isOn = isOn,
            showStatus = state.notificationEnabled.value,
            sinceMs = state.isOnTimestampMs.value,
            needsReapply = controller.needsReapply()
        )
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
