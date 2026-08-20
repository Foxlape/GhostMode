package com.ghostmode.app.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ghostmode.app.R
import com.ghostmode.app.data.GhostStateRepository
import com.ghostmode.app.data.PresetRepository
import com.ghostmode.app.domain.GhostModeController
import com.ghostmode.app.shell.AutoShellExecutor
import com.ghostmode.app.shell.RootShellExecutor
import com.ghostmode.app.shell.ShizukuManager
import com.ghostmode.app.shell.ShizukuStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class GhostTileService : TileService() {

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { updateTile() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { updateTile() }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ -> updateTile() }

    private lateinit var scope: CoroutineScope
    private lateinit var shizukuManager: ShizukuManager
    private lateinit var rootExecutor: RootShellExecutor
    private lateinit var autoExecutor: AutoShellExecutor
    private lateinit var presetRepository: PresetRepository
    private lateinit var stateRepository: GhostStateRepository
    private lateinit var ghostModeController: GhostModeController

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        shizukuManager = ShizukuManager(applicationContext)
        rootExecutor = RootShellExecutor()
        autoExecutor = AutoShellExecutor(rootExecutor, shizukuManager)
        presetRepository = PresetRepository(applicationContext)
        stateRepository = GhostStateRepository(applicationContext)
        ghostModeController = GhostModeController(autoExecutor, presetRepository, stateRepository)
        shizukuManager.start()
        registerShizukuListeners()
        scope.launch { rootExecutor.probeRoot(); updateTile() }
    }

    override fun onDestroy() {
        unregisterShizukuListeners()
        shizukuManager.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            rootExecutor.probeRoot()
            updateTile()
        }
    }

    override fun onClick() {
        handleTileClick()
    }

    private fun handleTileClick() {
        scope.launch {
            val hasRoot = rootExecutor.probeRoot()
            if (hasRoot || shizukuManager.status.value == ShizukuStatus.READY) {
                if (stateRepository.isOn.value) {
                    ghostModeController.turnOff()
                } else {
                    ghostModeController.turnOn()
                }
                com.ghostmode.app.service.StatusNotificationManager.update(
                    applicationContext,
                    stateRepository.isOn.value,
                    stateRepository.notificationEnabled.value,
                    stateRepository.isOnTimestampMs.value
                )
                com.ghostmode.app.widget.GhostWidgetProvider.refreshAll(applicationContext, stateRepository.isOn.value)
                updateTile()
                return@launch
            }
            when (shizukuManager.status.value) {
                ShizukuStatus.NO_PERMISSION -> requestShizukuPermission()
                else -> launchAppTrampoline()
            }
            updateTile()
        }
    }

    private fun launchAppTrampoline() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        collapseQuickSettingsWith(launchIntent)
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun collapseQuickSettingsWith(launchIntent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    LAUNCH_REQUEST_CODE,
                    launchIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            return
        }
        @Suppress("DEPRECATION")
        startActivityAndCollapse(launchIntent)
    }

    private fun requestShizukuPermission() {
        runShizukuCall { Shizuku.requestPermission(REQUEST_CODE) }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_label)
        tile.state = resolveTileState()
        tile.updateTile()
    }

    private fun resolveTileState(): Int {
        val status = shizukuManager.status.value
        val isBackendAvailable = rootExecutor.isRootAvailable.value ||
            status == ShizukuStatus.READY ||
            status == ShizukuStatus.NO_PERMISSION
        return when {
            stateRepository.isOn.value -> Tile.STATE_ACTIVE
            isBackendAvailable -> Tile.STATE_INACTIVE
            else -> Tile.STATE_UNAVAILABLE
        }
    }

    private fun registerShizukuListeners() {
        runShizukuCall { Shizuku.addBinderReceivedListenerSticky(binderReceivedListener) }
        runShizukuCall { Shizuku.addBinderDeadListener(binderDeadListener) }
        runShizukuCall { Shizuku.addRequestPermissionResultListener(permissionResultListener) }
    }

    private fun unregisterShizukuListeners() {
        runShizukuCall { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
        runShizukuCall { Shizuku.removeBinderDeadListener(binderDeadListener) }
        runShizukuCall { Shizuku.removeRequestPermissionResultListener(permissionResultListener) }
    }

    private fun runShizukuCall(action: () -> Unit) {
        try {
            action()
        } catch (_: IllegalStateException) {
        }
    }

    companion object {
        private const val REQUEST_CODE = 101
        private const val LAUNCH_REQUEST_CODE = 0

        fun requestTileUpdate(context: Context) {
            try {
                requestListeningState(
                    context,
                    android.content.ComponentName(context, GhostTileService::class.java)
                )
            } catch (_: Exception) {
            }
        }
    }
}
