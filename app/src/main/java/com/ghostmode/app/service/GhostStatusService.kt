package com.ghostmode.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ghostmode.app.GhostModeApp
import com.ghostmode.app.MainActivity
import com.ghostmode.app.R
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class GhostStatusService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var stateRepository: GhostStateRepository

    override fun onCreate() {
        super.onCreate()
        stateRepository = (applicationContext as? GhostModeApp)?.stateRepository
            ?: GhostStateRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (intent?.action == ACTION_TURN_OFF) handleTurnOff()
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundCompat() {
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            FOREGROUND_SERVICE_TYPE_NONE
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildStatusNotification(), foregroundServiceType)
    }

    private fun buildStatusNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ghost)
            .setContentTitle(getString(R.string.notification_title_on))
            .setContentText(getString(R.string.notification_text_on))
            .setOngoing(true)
            .setWhen(stateRepository.isOnTimestampMs.value)
            .setUsesChronometer(true)
            .setContentIntent(mainActivityPendingIntent())
            .addAction(R.drawable.ic_ghost, getString(R.string.notification_action_turn_off), turnOffPendingIntent())
            .build()

    private fun mainActivityPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        CONTENT_REQUEST_CODE,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun turnOffPendingIntent(): PendingIntent = PendingIntent.getService(
        this,
        TURN_OFF_REQUEST_CODE,
        Intent(this, GhostStatusService::class.java).setAction(ACTION_TURN_OFF),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun handleTurnOff() {
        serviceScope.launch {
            try {
                performTurnOff()
            } catch (_: IllegalStateException) {
            }
        }
    }

    private suspend fun performTurnOff() {
        val appContext = applicationContext
        val shizukuManager = ShizukuManager(appContext)
        try {
            shizukuManager.start()
            val rootExecutor = RootShellExecutor()
            val ghostModeController = GhostModeController(
                AutoShellExecutor(rootExecutor, shizukuManager),
                PresetRepository(appContext),
                stateRepository
            )
            rootExecutor.probeRoot()
            if (stateRepository.isOn.value) ghostModeController.turnOff()
        } finally {
            GhostWidgetProvider.refreshAll(appContext, stateRepository.isOn.value)
            shizukuManager.stop()
        }
    }

    companion object {
        const val ACTION_TURN_OFF = "com.ghostmode.app.service.TURN_OFF"

        private const val CHANNEL_ID = "ghost_mode_status"
        private const val NOTIFICATION_ID = 1
        private const val CONTENT_REQUEST_CODE = 0
        private const val TURN_OFF_REQUEST_CODE = 1
        private const val FOREGROUND_SERVICE_TYPE_NONE = 0
    }
}
