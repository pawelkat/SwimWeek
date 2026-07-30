package com.swimweek.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class WeekRangeTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val newYork = ZoneId.of("America/New_York")
    private val utc = ZoneId.of("UTC")

    @Test
    fun mondayWeek_containsMondayThroughSunday() {
        // Wednesday 2026-07-22 in Paris
        val week = WeekRange.of(LocalDate.of(2026, 7, 22), paris, DayOfWeek.MONDAY)

        val mondayStart = ZonedDateTime.of(2026, 7, 20, 0, 0, 0, 0, paris).toInstant()
        val nextMonday = ZonedDateTime.of(2026, 7, 27, 0, 0, 0, 0, paris).toInstant()

        assertEquals(mondayStart, week.start)
        assertEquals(nextMonday, week.endExclusive)
        assertTrue(week.containsSessionStart(mondayStart))
        assertTrue(
            week.containsSessionStart(
                ZonedDateTime.of(2026, 7, 26, 23, 59, 0, 0, paris).toInstant(),
            ),
        )
        assertFalse(week.containsSessionStart(nextMonday))
        assertFalse(
            week.containsSessionStart(
                ZonedDateTime.of(2026, 7, 19, 23, 59, 0, 0, paris).toInstant(),
            ),
        )
    }

    @Test
    fun sundayWeekStart_whenConfigured() {
        val week = WeekRange.of(LocalDate.of(2026, 7, 22), utc, DayOfWeek.SUNDAY)
        // Sunday 2026-07-19 00:00 UTC → Saturday 2026-07-25 exclusive end Sunday 2026-07-26
        assertEquals(
            ZonedDateTime.of(2026, 7, 19, 0, 0, 0, 0, utc).toInstant(),
            week.start,
        )
        assertEquals(
            ZonedDateTime.of(2026, 7, 26, 0, 0, 0, 0, utc).toInstant(),
            week.endExclusive,
        )
    }

    @Test
    fun identityKey_stableForSameWeek() {
        val a = WeekRange.of(LocalDate.of(2026, 7, 22), paris)
        val b = WeekRange.of(LocalDate.of(2026, 7, 25), paris)
        assertEquals(a.identityKey(), b.identityKey())
        assertEquals(a.start, b.start)
    }

    @Test
    fun identityKey_differsAcrossZones() {
        val date = LocalDate.of(2026, 7, 22)
        val a = WeekRange.of(date, paris)
        val b = WeekRange.of(date, newYork)
        // Same calendar date but different zone → different Instant start → different key
        assertTrue(a.identityKey() != b.identityKey() || a.start != b.start)
    }

    @Test
    fun sessionCrossingMidnightMonday_attributedToSundayWeek() {
        // Session starts Sunday 23:30, ends Monday 00:20 — whole session → week of start
        val sessionStart = ZonedDateTime.of(2026, 7, 19, 23, 30, 0, 0, paris).toInstant()
        val weekOfStart = WeekRange.forSessionStart(sessionStart, paris, DayOfWeek.MONDAY)

        // Monday week starting 2026-07-20 should NOT contain this session
        val mondayWeek = WeekRange.of(LocalDate.of(2026, 7, 20), paris, DayOfWeek.MONDAY)
        assertFalse(mondayWeek.containsSessionStart(sessionStart))
        assertTrue(weekOfStart.containsSessionStart(sessionStart))
        assertEquals(
            ZonedDateTime.of(2026, 7, 13, 0, 0, 0, 0, paris).toInstant(),
            weekOfStart.start,
        )
    }

    @Test
    fun current_usesClockInstant() {
        val fixed = ZonedDateTime.of(2026, 7, 22, 15, 0, 0, 0, paris).toInstant()
        val clock = Clock.fixed(fixed, paris)
        val week = WeekRange.current(paris, DayOfWeek.MONDAY, clock)
        assertEquals(
            ZonedDateTime.of(2026, 7, 20, 0, 0, 0, 0, paris).toInstant(),
            week.start,
        )
    }

    @Test
    fun dstSpringForward_europeParis_weekContainingTransition() {
        // DST starts 2026-03-29 (Sun) 02:00 → 03:00; Monday week 2026-03-23 contains it.
        val week = WeekRange.of(LocalDate.of(2026, 3, 25), paris, DayOfWeek.MONDAY)
        assertEquals(
            ZonedDateTime.of(2026, 3, 23, 0, 0, 0, 0, paris).toInstant(),
            week.start,
        )
        assertEquals(
            ZonedDateTime.of(2026, 3, 30, 0, 0, 0, 0, paris).toInstant(),
            week.endExclusive,
        )
        val hours = java.time.Duration.between(week.start, week.endExclusive).toHours()
        assertEquals(167L, hours) // 7*24 - 1
    }

    @Test
    fun dstFallBack_europeParis_weekContainingTransition() {
        // DST ends 2026-10-25 (Sun); Monday week 2026-10-19 contains it.
        val week = WeekRange.of(LocalDate.of(2026, 10, 22), paris, DayOfWeek.MONDAY)
        assertEquals(
            ZonedDateTime.of(2026, 10, 19, 0, 0, 0, 0, paris).toInstant(),
            week.start,
        )
        val hours = java.time.Duration.between(week.start, week.endExclusive).toHours()
        assertEquals(169L, hours) // 7*24 + 1
    }
}
