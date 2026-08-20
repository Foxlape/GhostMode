package com.ghostmode.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ghostmode.app.MainActivity
import com.ghostmode.app.R

object StatusNotificationManager {

    private const val CHANNEL_ID = "ghost_mode_status"
    private const val NOTIFICATION_ID = 1001
    private const val CONTENT_REQUEST_CODE = 10
    private const val TURN_OFF_REQUEST_CODE = 11

    fun update(
        context: Context,
        isOn: Boolean,
        notificationEnabled: Boolean,
        timestampMs: Long
    ) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (!isOn || !notificationEnabled) {
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }

        createNotificationChannel(context)

        val mainActivityPendingIntent = PendingIntent.getActivity(
            context,
            CONTENT_REQUEST_CODE,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val turnOffIntent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_TURN_OFF)
        val turnOffPendingIntent = PendingIntent.getBroadcast(
            context,
            TURN_OFF_REQUEST_CODE,
            turnOffIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ghost)
            .setContentTitle(context.getString(R.string.notification_title_on))
            .setContentText(context.getString(R.string.notification_text_on))
            .setOngoing(true)
            .setWhen(if (timestampMs > 0) timestampMs else System.currentTimeMillis())
            .setUsesChronometer(true)
            .setContentIntent(mainActivityPendingIntent)
            .addAction(
                R.drawable.ic_ghost,
                context.getString(R.string.notification_action_turn_off),
                turnOffPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_name)
            setShowBadge(false)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }
}
