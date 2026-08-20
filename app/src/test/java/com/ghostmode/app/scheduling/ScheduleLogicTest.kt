package com.ghostmode.app.scheduling

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleLogicTest {

    private fun isMinuteInWindow(minuteOfDay: Int, startMinute: Int, endMinute: Int): Boolean {
        return if (startMinute <= endMinute) {
            minuteOfDay in startMinute until endMinute
        } else {
            minuteOfDay >= startMinute || minuteOfDay < endMinute
        }
    }

    @Test
    fun overnightWindow_atMidnight_isInWindow() {
        val start = 23 * 60 // 23:00 = 1380
        val end = 8 * 60    // 08:00 = 480
        val midnight = 0

        assertTrue(isMinuteInWindow(midnight, start, end))
        assertTrue(isMinuteInWindow(23 * 60 + 30, start, end))
        assertTrue(isMinuteInWindow(7 * 60 + 59, start, end))
        assertFalse(isMinuteInWindow(8 * 60, start, end))
        assertFalse(isMinuteInWindow(14 * 60, start, end))
    }

    @Test
    fun daytimeWindow_isInWindow() {
        val start = 9 * 60  // 09:00 = 540
        val end = 18 * 60   // 18:00 = 1080

        assertTrue(isMinuteInWindow(12 * 60, start, end))
        assertFalse(isMinuteInWindow(8 * 60, start, end))
        assertFalse(isMinuteInWindow(19 * 60, start, end))
    }
}
