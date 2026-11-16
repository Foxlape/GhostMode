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

    /** Day mask with every day of the week set. Monday is bit 0, Sunday is bit 6. */
    const val ALL_DAYS = 0b1111111
    const val WEEKDAYS = 0b0011111
    const val WEEKEND = 0b1100000

    /** Mask bit for a [Calendar.DAY_OF_WEEK] value. */
    fun dayBit(calendarDayOfWeek: Int): Int = 1 shl ((calendarDayOfWeek + 5) % 7)

    /** [Calendar.DAY_OF_WEEK] values in display order, starting from [firstDayOfWeek]. */
    fun week(firstDayOfWeek: Int): List<Int> = (0 until 7).map { (firstDayOfWeek - 1 + it) % 7 + 1 }

    /**
     * Whether the window is active at [nowMs]. A window that runs past midnight belongs to the
     * day it starts on: Friday 23:00 – 08:00 also covers Saturday morning.
     */
    fun isActive(nowMs: Long, startMinute: Int, endMinute: Int, days: Int): Boolean {
        if (startMinute == endMinute || days and ALL_DAYS == 0) return false
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMs }
        val minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val today = days and dayBit(calendar.get(Calendar.DAY_OF_WEEK)) != 0
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = days and dayBit(calendar.get(Calendar.DAY_OF_WEEK)) != 0
        return if (startMinute < endMinute) {
            today && minute in startMinute until endMinute
        } else {
            (today && minute >= startMinute) || (yesterday && minute < endMinute)
        }
    }

    /** Nearest start or end (strictly after [nowMs]) of a window on one of [days], or `null` if there is none. */
    fun nextBoundaryMs(nowMs: Long, startMinute: Int, endMinute: Int, days: Int): Long? {
        if (startMinute == endMinute || days and ALL_DAYS == 0) return null
        var best: Long? = null
        // Yesterday's overnight window may still have its end ahead of us.
        for (offset in -1..7) {
            val day = Calendar.getInstance().apply {
                timeInMillis = nowMs
                add(Calendar.DAY_OF_YEAR, offset)
            }
            if (days and dayBit(day.get(Calendar.DAY_OF_WEEK)) == 0) continue
            val startMs = atMinute(day, startMinute)
            if (endMinute < startMinute) day.add(Calendar.DAY_OF_YEAR, 1)
            val endMs = atMinute(day, endMinute)
            for (candidate in longArrayOf(startMs, endMs)) {
                if (candidate > nowMs && (best == null || candidate < best)) best = candidate
            }
        }
        return best
    }

    private fun atMinute(day: Calendar, minuteOfDay: Int): Long = (day.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
