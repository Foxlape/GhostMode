package com.ghostmode.app

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
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
        bootCount = { readBootCount() },
        activeSlots = { readActiveSlots() }
    )

    val actions = GhostActions(appContext, controller, state, scope)

    val updates = UpdateManager(scope)

    /** Slots whose SIM is ready; `getSimState(slot)` needs no permission. */
    private fun readActiveSlots(): Set<Int>? {
        val telephony = appContext.getSystemService(TelephonyManager::class.java) ?: return null
        val slotCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            telephony.activeModemCount
        } else {
            @Suppress("DEPRECATION")
            telephony.phoneCount
        }
        return try {
            (0 until slotCount).filter { telephony.getSimState(it) == TelephonyManager.SIM_STATE_READY }.toSet()
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun readBootCount(): Int =
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, AppliedSnapshot.BOOT_COUNT_UNKNOWN)
}

val Context.appGraph: AppGraph
    get() = (applicationContext as GhostModeApp).graph
