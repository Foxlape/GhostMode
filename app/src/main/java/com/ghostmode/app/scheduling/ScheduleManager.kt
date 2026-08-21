package com.ghostmode.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ghostmode.app.data.GhostStateRepository
import java.util.Calendar

object ScheduleManager {

    fun update(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val stateRepository = GhostStateRepository.getInstance(appContext)
        val startPendingIntent = buildTickPendingIntent(appContext, REQUEST_CODE_START)
        val endPendingIntent = buildTickPendingIntent(appContext, REQUEST_CODE_END)
        alarmManager.cancel(startPendingIntent)
        alarmManager.cancel(endPendingIntent)
        if (!stateRepository.scheduleEnabled.value) return
        val nowMs = System.currentTimeMillis()
        val startAtMs = nextBoundaryMs(nowMs, stateRepository.scheduleStartMinuteOfDay.value)
        val endAtMs = nextBoundaryMs(nowMs, stateRepository.scheduleEndMinuteOfDay.value)
        val isStartNearest = startAtMs <= endAtMs
        val triggerAtMs = if (isStartNearest) startAtMs else endAtMs
        val pendingIntent = if (isStartNearest) startPendingIntent else endPendingIntent
        if (canScheduleExact(alarmManager)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
        } else {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMs, ALARM_WINDOW_LENGTH_MS, pendingIntent)
        }
    }

    private fun buildTickPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val tickIntent = Intent(context, ScheduleReceiver::class.java).setAction(ScheduleReceiver.ACTION_TICK)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            tickIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun nextBoundaryMs(nowMs: Long, minuteOfDay: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowMs
        calendar.set(Calendar.HOUR_OF_DAY, minuteOfDay / MINUTES_PER_HOUR)
        calendar.set(Calendar.MINUTE, minuteOfDay % MINUTES_PER_HOUR)
        calendar.set(Calendar.SECOND, SECOND_OF_MINUTE)
        calendar.set(Calendar.MILLISECOND, MILLISECOND_OF_SECOND)
        if (calendar.timeInMillis <= nowMs) {
            calendar.add(Calendar.DAY_OF_YEAR, DAY_SHIFT)
        }
        return calendar.timeInMillis
    }

    private const val REQUEST_CODE_START = 0
    private const val REQUEST_CODE_END = 1
    private const val MINUTES_PER_HOUR = 60
    private const val SECOND_OF_MINUTE = 0
    private const val MILLISECOND_OF_SECOND = 0
    private const val DAY_SHIFT = 1
    private const val ALARM_WINDOW_LENGTH_MS = 120_000L
}
