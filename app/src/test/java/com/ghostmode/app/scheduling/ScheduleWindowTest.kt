package com.ghostmode.app.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleWindowTest {

    @Test
    fun overnightWindow() {
        val start = 23 * 60
        val end = 8 * 60
        assertTrue(ScheduleWindow.contains(0, start, end))
        assertTrue(ScheduleWindow.contains(23 * 60 + 30, start, end))
        assertTrue(ScheduleWindow.contains(7 * 60 + 59, start, end))
        assertFalse(ScheduleWindow.contains(8 * 60, start, end))
        assertFalse(ScheduleWindow.contains(14 * 60, start, end))
    }

    @Test
    fun daytimeWindow() {
        assertTrue(ScheduleWindow.contains(12 * 60, 9 * 60, 18 * 60))
        assertFalse(ScheduleWindow.contains(8 * 60, 9 * 60, 18 * 60))
        assertFalse(ScheduleWindow.contains(18 * 60, 9 * 60, 18 * 60))
    }

    @Test
    fun emptyWindow_neverContains() {
        assertFalse(ScheduleWindow.contains(600, 600, 600))
    }

    @Test
    fun durationWrapsOverMidnight() {
        assertEquals(9 * 60, ScheduleWindow.durationMinutes(23 * 60, 8 * 60))
        assertEquals(9 * 60, ScheduleWindow.durationMinutes(9 * 60, 18 * 60))
    }

    @Test
    fun nextOccurrence_isStrictlyInTheFuture() {
        val now = System.currentTimeMillis()
        val next = ScheduleWindow.nextOccurrenceMs(now, ScheduleWindow.currentMinuteOfDay(now))
        assertTrue(next > now)
        assertTrue(next - now <= 24 * 3_600_000L)
    }

    // 2026-11-16 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val friday = 20
    private val saturday = 21
    private val sunday = 22
    private val monday = 23

    @Test
    fun weekdayWindow_skipsTheWeekend() {
        val days = ScheduleWindow.WEEKDAYS
        assertTrue(ScheduleWindow.isActive(at(friday, 12), 8 * 60, 20 * 60, days))
        assertFalse(ScheduleWindow.isActive(at(saturday, 12), 8 * 60, 20 * 60, days))
        assertFalse(ScheduleWindow.isActive(at(friday, 20), 8 * 60, 20 * 60, days))
    }

    @Test
    fun overnightWindow_belongsToTheDayItStarts() {
        val days = ScheduleWindow.dayBit(Calendar.FRIDAY)
        assertTrue(ScheduleWindow.isActive(at(friday, 23, 30), 23 * 60, 8 * 60, days))
        assertTrue(ScheduleWindow.isActive(at(saturday, 7), 23 * 60, 8 * 60, days))
        assertFalse(ScheduleWindow.isActive(at(saturday, 23, 30), 23 * 60, 8 * 60, days))
        assertFalse(ScheduleWindow.isActive(at(friday, 7), 23 * 60, 8 * 60, days))
    }

    @Test
    fun allDays_matchesTheDailyWindow() {
        for (hour in 0 until 24) {
            val now = at(saturday, hour, 15)
            assertEquals(
                ScheduleWindow.contains(hour * 60 + 15, 23 * 60, 8 * 60),
                ScheduleWindow.isActive(now, 23 * 60, 8 * 60, ScheduleWindow.ALL_DAYS)
            )
        }
    }

    @Test
    fun nextBoundary_jumpsOverTheWeekend() {
        val days = ScheduleWindow.WEEKDAYS
        assertEquals(at(friday, 20), ScheduleWindow.nextBoundaryMs(at(friday, 12), 8 * 60, 20 * 60, days))
        assertEquals(at(monday, 8), ScheduleWindow.nextBoundaryMs(at(friday, 21), 8 * 60, 20 * 60, days))
        assertEquals(at(monday, 8), ScheduleWindow.nextBoundaryMs(at(sunday, 12), 8 * 60, 20 * 60, days))
    }

    @Test
    fun nextBoundary_findsYesterdaysOvernightEnd() {
        val days = ScheduleWindow.dayBit(Calendar.FRIDAY)
        assertEquals(at(saturday, 8), ScheduleWindow.nextBoundaryMs(at(saturday, 2), 23 * 60, 8 * 60, days))
        assertEquals(at(friday + 7, 23), ScheduleWindow.nextBoundaryMs(at(saturday, 9), 23 * 60, 8 * 60, days))
    }

    @Test
    fun noDays_neverActive() {
        assertFalse(ScheduleWindow.isActive(at(friday, 12), 8 * 60, 20 * 60, 0))
        assertEquals(null, ScheduleWindow.nextBoundaryMs(at(friday, 12), 8 * 60, 20 * 60, 0))
    }

    @Test
    fun weekOrder_followsTheLocale() {
        assertEquals(Calendar.MONDAY, ScheduleWindow.week(Calendar.MONDAY).first())
        assertEquals(Calendar.SUNDAY, ScheduleWindow.week(Calendar.MONDAY).last())
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), ScheduleWindow.week(Calendar.SUNDAY))
        assertEquals(ScheduleWindow.WEEKDAYS or ScheduleWindow.WEEKEND, ScheduleWindow.ALL_DAYS)
        assertEquals(1, ScheduleWindow.dayBit(Calendar.MONDAY))
        assertEquals(64, ScheduleWindow.dayBit(Calendar.SUNDAY))
    }
}
