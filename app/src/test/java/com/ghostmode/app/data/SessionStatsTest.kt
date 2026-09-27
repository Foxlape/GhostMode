package com.ghostmode.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStatsTest {

    private val hour = 3_600_000L

    @Test
    fun durationInWindow_clipsSessionsCrossingBoundaries() {
        val sessions = listOf(
            GhostSession(startMs = 1, endMs = 10 * hour),
            GhostSession(startMs = 20 * hour, endMs = GhostSession.SESSION_END_OPEN)
        )

        assertEquals(2 * hour, SessionStats.durationInWindow(sessions, fromMs = 8 * hour, toMs = 12 * hour))
        assertEquals(4 * hour, SessionStats.durationInWindow(sessions, fromMs = 18 * hour, toMs = 24 * hour))
        assertEquals(0L, SessionStats.durationInWindow(sessions, fromMs = 11 * hour, toMs = 19 * hour))
    }

    @Test
    fun dailyTotals_hasOneBucketPerDayEndingToday() {
        val now = SessionStats.startOfDay(System.currentTimeMillis()) + 12 * hour
        val sessions = listOf(GhostSession(now - 2 * hour, now - hour))

        val totals = SessionStats.dailyTotals(sessions, now)

        assertEquals(7, totals.size)
        assertEquals(hour, totals.last())
        assertEquals(0L, totals.dropLast(1).sum())
    }
}
