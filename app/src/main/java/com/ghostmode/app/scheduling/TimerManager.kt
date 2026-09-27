package com.ghostmode.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ghostmode.app.service.TimerReceiver

/** Alarm that turns Ghost Mode off at a given moment ("auto turn-off" timer). */
object TimerManager {

    const val MORNING_MINUTE_OF_DAY = 8 * 60

    fun schedule(context: Context, fireAtMs: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        ScheduleManager.setAlarm(alarmManager, fireAtMs, pendingIntent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    fun morningBoundaryMs(nowMs: Long): Long = ScheduleWindow.nextOccurrenceMs(nowMs, MORNING_MINUTE_OF_DAY)

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            TIMER_REQUEST_CODE,
            Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_TIMER_FIRE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private const val TIMER_REQUEST_CODE = 42
}
