package com.ghostmode.app.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
