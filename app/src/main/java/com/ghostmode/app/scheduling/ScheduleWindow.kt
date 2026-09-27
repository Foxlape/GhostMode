package com.ghostmode.app.scheduling

import java.util.Calendar

/** Daily time window `[start, end)` in minutes of day; wraps over midnight when `start > end`. */
object ScheduleWindow {

    const val MINUTES_PER_DAY = 24 * 60

    fun contains(minuteOfDay: Int, startMinute: Int, endMinute: Int): Boolean = when {
        startMinute == endMinute -> false
        startMinute < endMinute -> minuteOfDay in startMinute until endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
    }

    fun currentMinuteOfDay(nowMs: Long = System.currentTimeMillis()): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMs }
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    /** Next moment (strictly after [nowMs]) when the wall clock shows [minuteOfDay]. */
    fun nextOccurrenceMs(nowMs: Long, minuteOfDay: Int): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= nowMs) calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    fun durationMinutes(startMinute: Int, endMinute: Int): Int =
        ((endMinute - startMinute) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY
}
