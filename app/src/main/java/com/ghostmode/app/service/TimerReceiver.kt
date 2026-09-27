package com.ghostmode.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ghostmode.app.appGraph
import com.ghostmode.app.domain.TurnOutcome

/** Fires when the auto turn-off timer expires. */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TIMER_FIRE) return
        val graph = context.appGraph
        val pendingResult = goAsync()
        graph.actions.launch {
            try {
                if (graph.state.isOn.value && graph.state.timerFireAtMs.value > 0L) {
                    val outcome = turnOff()
                    if (outcome is TurnOutcome.Failure && graph.state.isOn.value) {
                        // Backend not reachable yet (e.g. Shizuku stopped) — try again shortly.
                        armTimerAt(System.currentTimeMillis() + RETRY_DELAY_MS)
                    }
                } else {
                    graph.state.clearTimer()
                }
            } catch (error: Exception) {
                Log.e(TAG, "Timed turn-off failed", error)
            } finally {
                refreshSurfaces()
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "GhostTimer"
        private const val RETRY_DELAY_MS = 2 * 60_000L
        const val ACTION_TIMER_FIRE = "com.ghostmode.app.timer.FIRE"
    }
}
