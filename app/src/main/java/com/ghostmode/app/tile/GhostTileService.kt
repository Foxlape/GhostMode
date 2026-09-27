package com.ghostmode.app.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ghostmode.app.MainActivity
import com.ghostmode.app.R
import com.ghostmode.app.appGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

class GhostTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listeningJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val graph = appGraph
        listeningJob?.cancel()
        listeningJob = combine(graph.state.isOn, graph.shell.backend, graph.controller.isBusy) { _, _, _ -> updateTile() }
            .launchIn(scope)
        graph.shizuku.refresh()
    }

    override fun onStopListening() {
        listeningJob?.cancel()
        listeningJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        // Changing call reachability from the lock screen requires unlocking first.
        if (isSecure) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        val graph = appGraph
        scope.launch {
            if (!graph.shell.awaitReady(QUICK_READY_TIMEOUT_MS)) {
                openApp()
                return@launch
            }
            // The app scope outlives this service, so the command sequence is never cut short.
            graph.actions.launch { this.toggle() }
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, REQUEST_OPEN_APP, intent, PendingIntent.FLAG_IMMUTABLE)
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (_: Exception) {
            // The tile may already be detached; nothing sensible to do.
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val graph = appGraph
        val isOn = graph.state.isOn.value
        val isBusy = graph.controller.isBusy.value
        val hasBackend = graph.shell.backend.value != null
        tile.label = getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_ghost)
        val subtitle = getString(
            when {
                isBusy -> R.string.tile_subtitle_busy
                isOn -> R.string.tile_subtitle_on
                hasBackend -> R.string.tile_subtitle_off
                else -> R.string.tile_subtitle_setup
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = subtitle
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tile.stateDescription = subtitle
        tile.state = if (isOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    companion object {
        private const val REQUEST_OPEN_APP = 0
        private const val QUICK_READY_TIMEOUT_MS = 2_000L

        fun requestTileUpdate(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, GhostTileService::class.java))
            } catch (_: Exception) {
                // Throws if the tile was never added; harmless.
            }
        }
    }
}
