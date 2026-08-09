package com.swimweek.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekRangePreviousTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun previousWeek_shiftsSevenDays() {
        val week = WeekRange.of(LocalDate.of(2026, 7, 22), zone, DayOfWeek.MONDAY)
        val prev = week.previous()
        assertEquals(LocalDate.of(2026, 7, 13), prev.startLocalDate())
        assertTrue(prev.endExclusive == week.start)
    }
}
