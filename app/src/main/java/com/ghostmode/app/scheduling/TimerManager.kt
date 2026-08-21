package com.ghostmode.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ghostmode.app.service.TimerReceiver
import java.util.Calendar

object TimerManager {

    fun schedule(context: Context, fireAtMs: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        if (!canScheduleExact(alarmManager)) {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                fireAtMs,
                ALARM_WINDOW_LENGTH_MS,
                buildPendingIntent(context)
            )
            return
        }
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            fireAtMs,
            buildPendingIntent(context)
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(buildPendingIntent(context))
    }

    fun morningBoundaryMs(nowMs: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowMs
        calendar.set(Calendar.HOUR_OF_DAY, MORNING_HOUR)
        calendar.set(Calendar.MINUTE, MINUTE_ZERO)
        calendar.set(Calendar.SECOND, SECOND_ZERO)
        calendar.set(Calendar.MILLISECOND, MILLISECOND_ZERO)
        if (calendar.timeInMillis <= nowMs) {
            calendar.add(Calendar.DAY_OF_YEAR, DAY_SHIFT)
        }
        return calendar.timeInMillis
    }

    private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun buildPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            TIMER_REQUEST_CODE,
            Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_TIMER_FIRE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private const val TIMER_REQUEST_CODE = 42
    private const val MORNING_HOUR = 8
    private const val MINUTE_ZERO = 0
    private const val SECOND_ZERO = 0
    private const val MILLISECOND_ZERO = 0
    private const val DAY_SHIFT = 1
    private const val ALARM_WINDOW_LENGTH_MS = 120_000L
}
