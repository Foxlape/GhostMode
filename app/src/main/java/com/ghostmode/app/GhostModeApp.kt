package com.ghostmode.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.ghostmode.app.scheduling.ScheduleManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn

class GhostModeApp : Application() {

    lateinit var graph: AppGraph
        private set

    /** Lets screenshot tests start from pre-seeded preferences. */
    @androidx.annotation.VisibleForTesting
    internal fun replaceGraphForTest(newGraph: AppGraph) {
        graph = newGraph
    }

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        AppCompatDelegate.setDefaultNightMode(graph.state.themeMode.value.toAppCompatNightMode())
        ScheduleManager.update(this)
        if (graph.state.updateCheckEnabled.value) graph.updates.check()

        // Keep tile, widget and notification in sync with every state change, whoever made it.
        combine(
            graph.state.isOn,
            graph.state.notificationEnabled,
            graph.state.appliedSnapshot
        ) { _, _, _ -> graph.actions.refreshSurfaces() }
            .launchIn(graph.scope)
    }
}
