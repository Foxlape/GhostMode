package com.ghostmode.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.ghostmode.app.MainActivity
import com.ghostmode.app.R
import com.ghostmode.app.data.GhostStateRepository
import com.ghostmode.app.data.PresetRepository
import com.ghostmode.app.domain.GhostModeController
import com.ghostmode.app.shell.AutoShellExecutor
import com.ghostmode.app.shell.RootShellExecutor
import com.ghostmode.app.shell.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GhostWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val isOn = GhostStateRepository.getInstance(context).isOn.value
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context, isOn))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return
        handleToggle(context)
    }

    private fun handleToggle(context: Context) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                performToggle(appContext)
            } catch (error: Exception) {
                Log.e(TAG, "Widget toggle failed", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun performToggle(context: Context) {
        val shizukuManager = ShizukuManager(context)
        val stateRepository = GhostStateRepository.getInstance(context)
        try {
            shizukuManager.start()
            val rootExecutor = RootShellExecutor()
            val hasRoot = rootExecutor.probeRoot()
            val ghostModeController = GhostModeController(
                AutoShellExecutor(rootExecutor, shizukuManager),
                PresetRepository.getInstance(context),
                stateRepository
            )
            if (hasRoot || shizukuManager.status.value == com.ghostmode.app.shell.ShizukuStatus.READY) {
                if (stateRepository.isOn.value) ghostModeController.turnOff() else ghostModeController.turnOn()
            }
            com.ghostmode.app.service.StatusNotificationManager.update(
                context,
                stateRepository.isOn.value,
                stateRepository.notificationEnabled.value,
                stateRepository.isOnTimestampMs.value
            )
        } finally {
            refreshAll(context, stateRepository.isOn.value)
            shizukuManager.stop()
        }
    }

    companion object {
        private const val TAG = "GhostWidget"

        const val ACTION_TOGGLE = "com.ghostmode.app.widget.TOGGLE"

        fun refreshAll(context: Context, isOn: Boolean) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val remoteViews = buildRemoteViews(context, isOn)
            val providerComponent = ComponentName(context, GhostWidgetProvider::class.java)
            appWidgetManager.getAppWidgetIds(providerComponent).forEach { appWidgetId ->
                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            }
        }

        private fun buildRemoteViews(context: Context, isOn: Boolean): RemoteViews {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_ghost)
            remoteViews.setImageViewResource(R.id.widget_icon, R.drawable.ic_launcher_foreground)
            remoteViews.setInt(R.id.widget_icon, IMAGE_ALPHA_METHOD, if (isOn) ALPHA_ON else ALPHA_OFF)
            remoteViews.setInt(
                R.id.widget_icon,
                COLOR_FILTER_METHOD,
                if (isOn) COLOR_ON else COLOR_OFF
            )
            val toggleIntent = Intent(context, GhostWidgetProvider::class.java).setAction(ACTION_TOGGLE)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                TOGGLE_REQUEST_CODE,
                toggleIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_icon, pendingIntent)
            val openIntent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val openPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_OPEN,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_open, openPendingIntent)
            return remoteViews
        }

        private const val TOGGLE_REQUEST_CODE = 0
        private const val REQUEST_CODE_OPEN = 1
        private const val ALPHA_ON = 255
        private const val ALPHA_OFF = 90
        private const val COLOR_ON = 0xFF8C9EFF.toInt()
        private const val COLOR_OFF = 0xFFFFFFFF.toInt()
        private const val IMAGE_ALPHA_METHOD = "setImageAlpha"
        private const val COLOR_FILTER_METHOD = "setColorFilter"
    }
}
