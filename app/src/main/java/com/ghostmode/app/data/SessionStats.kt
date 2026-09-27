package com.ghostmode.app.data

import java.util.Calendar

/** Pure helpers for usage statistics. Durations are clipped to the requested window. */
object SessionStats {

    fun durationInWindow(sessions: List<GhostSession>, fromMs: Long, toMs: Long): Long =
        sessions.sumOf { session ->
            val end = if (session.isOpen) toMs else session.endMs
            (minOf(end, toMs) - maxOf(session.startMs, fromMs)).coerceAtLeast(0L)
        }

    fun startOfDay(timeMs: Long, dayOffset: Int = 0): Long =
        Calendar.getInstance().apply {
            timeInMillis = timeMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }.timeInMillis

    /** Total time per calendar day for the last [days] days, oldest first; the last item is today. */
    fun dailyTotals(sessions: List<GhostSession>, nowMs: Long, days: Int = 7): List<Long> =
        (days - 1 downTo 0).map { back ->
            val from = startOfDay(nowMs, -back)
            val to = minOf(startOfDay(nowMs, -back + 1), nowMs)
            durationInWindow(sessions, from, to)
        }
}
