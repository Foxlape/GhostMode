package com.ghostmode.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ghostmode.app.appGraph

/** Keeps exactly one alarm armed for the nearest schedule boundary. */
object ScheduleManager {

    fun update(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val state = appContext.appGraph.state
        val pendingIntent = tickPendingIntent(appContext)
        alarmManager.cancel(pendingIntent)
        if (!state.scheduleEnabled.value) return
        val start = state.scheduleStartMinuteOfDay.value
        val end = state.scheduleEndMinuteOfDay.value
        if (start == end) return
        val nowMs = System.currentTimeMillis()
        val triggerAtMs = minOf(
            ScheduleWindow.nextOccurrenceMs(nowMs, start),
            ScheduleWindow.nextOccurrenceMs(nowMs, end)
        )
        setAlarm(alarmManager, triggerAtMs, pendingIntent)
    }

    fun canScheduleExact(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    internal fun setAlarm(alarmManager: AlarmManager, triggerAtMs: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
        } else {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMs, ALARM_WINDOW_LENGTH_MS, pendingIntent)
        }
    }

    private fun tickPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_TICK,
            Intent(context, ScheduleReceiver::class.java).setAction(ScheduleReceiver.ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private const val REQUEST_CODE_TICK = 0
    private const val ALARM_WINDOW_LENGTH_MS = 120_000L
}
