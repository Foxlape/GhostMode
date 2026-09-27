package com.ghostmode.app

import android.content.Context
import android.provider.Settings
import com.ghostmode.app.data.AppliedSnapshot
import com.ghostmode.app.data.GhostStateRepository
import com.ghostmode.app.data.PresetRepository
import com.ghostmode.app.data.SharedPreferencesStore
import com.ghostmode.app.domain.GhostModeController
import com.ghostmode.app.shell.AutoShellExecutor
import com.ghostmode.app.shell.RootShellExecutor
import com.ghostmode.app.shell.ShizukuManager
import com.ghostmode.app.support.UpdateManager
import com.ghostmode.app.system.GhostActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide object graph. Activity, tile, widget and receivers all share the same shell
 * connection, controller (and therefore the same busy lock) and state.
 */
class AppGraph(context: Context) {

    private val appContext = context.applicationContext

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val state = GhostStateRepository(
        SharedPreferencesStore(appContext.getSharedPreferences(GhostStateRepository.PREFS_NAME, Context.MODE_PRIVATE))
    )

    val presets = PresetRepository(
        SharedPreferencesStore(appContext.getSharedPreferences(PresetRepository.PREFS_NAME, Context.MODE_PRIVATE))
    )

    val root = RootShellExecutor()

    val shizuku = ShizukuManager(appContext).also { it.start() }

    val shell = AutoShellExecutor(root, shizuku, scope)

    val controller = GhostModeController(
        shell = shell,
        presets = presets,
        state = state,
        bootCount = { readBootCount() }
    )

    val actions = GhostActions(appContext, controller, state, scope)

    val updates = UpdateManager(scope)

    private fun readBootCount(): Int =
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, AppliedSnapshot.BOOT_COUNT_UNKNOWN)
}

val Context.appGraph: AppGraph
    get() = (applicationContext as GhostModeApp).graph
