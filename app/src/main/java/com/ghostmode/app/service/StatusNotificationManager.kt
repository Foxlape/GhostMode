package com.ghostmode.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ghostmode.app.MainActivity
import com.ghostmode.app.R

object StatusNotificationManager {

    private const val CHANNEL_STATUS = "ghost_mode_status"
    private const val CHANNEL_ATTENTION = "ghost_mode_attention"
    private const val NOTIFICATION_ID = 1001
    private const val REQUEST_CONTENT = 10
    private const val REQUEST_TURN_OFF = 11
    private const val REQUEST_REAPPLY = 12

    /**
     * Shows the ongoing status notification while the mode is on (if enabled by the user), or an
     * attention notification when a reboot reverted part of the applied state.
     */
    fun update(context: Context, isOn: Boolean, showStatus: Boolean, sinceMs: Long, needsReapply: Boolean) {
        val manager = NotificationManagerCompat.from(context)
        if (!isOn || (!showStatus && !needsReapply)) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        createChannels(context)

        val builder = NotificationCompat.Builder(context, if (needsReapply) CHANNEL_ATTENTION else CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_ghost)
            .setColor(ContextCompat.getColor(context, R.color.ghost_accent))
            .setContentIntent(contentIntent(context))
            .setOngoing(!needsReapply)
            .setSilent(!needsReapply)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_ghost, context.getString(R.string.notification_action_turn_off), actionIntent(context, NotificationActionReceiver.ACTION_TURN_OFF, REQUEST_TURN_OFF))

        if (needsReapply) {
            builder
                .setContentTitle(context.getString(R.string.notification_reapply_title))
                .setContentText(context.getString(R.string.notification_reapply_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_reapply_text)))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(R.drawable.ic_ghost, context.getString(R.string.action_reapply), actionIntent(context, NotificationActionReceiver.ACTION_REAPPLY, REQUEST_REAPPLY))
        } else {
            builder
                .setContentTitle(context.getString(R.string.notification_title_on))
                .setContentText(context.getString(R.string.notification_text_on))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setWhen(if (sinceMs > 0) sinceMs else System.currentTimeMillis())
                .setShowWhen(true)
                .setUsesChronometer(true)
        }

        try {
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted.
        }
    }

    private fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_CONTENT,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, NotificationActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.notification_channel_status),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_status_desc)
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ATTENTION,
                context.getString(R.string.notification_channel_attention),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_attention_desc)
            }
        )
    }
}
