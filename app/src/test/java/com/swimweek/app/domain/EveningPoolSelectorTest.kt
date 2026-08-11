package com.swimweek.app.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EveningPoolSelectorTest {

    private val city = Pool(
        id = "city",
        name = "Hallenbad City",
        kind = "hallenbad",
        category = "indoor",
        openingHours = listOf(
            OpeningHourSlot(
                days = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun"),
                open = "06:00",
                close = "22:00",
            ),
        ),
    )

    private val blasi = Pool(
        id = "blaesi",
        name = "Hallenbad Bläsi",
        kind = "hallenbad",
        category = "indoor",
        openingHours = listOf(
            OpeningHourSlot(days = listOf("mon"), open = "12:00", close = "19:00"),
        ),
    )

    private val outdoor = Pool(
        id = "allenmoos",
        name = "Freibad Allenmoos",
        kind = "freibad",
        category = "outdoor",
        openingHours = listOf(
            OpeningHourSlot(
                period = "30. Mai–16. August",
                open = "14:00",
                close = "21:00",
                condition = "fair_weather",
            ),
        ),
    )

    @Test
    fun picksLatestClosingPoolOnMonday() {
        val date = LocalDate.of(2026, 8, 10) // Monday
        val selection = EveningPoolSelector.selectForDate(
            pools = listOf(city, blasi, outdoor),
            date = date,
            seasonYear = 2026,
        )
        assertNotNull(selection)
        assertEquals("city", selection!!.pool.id)
        assertEquals(LocalTime.of(22, 0), selection.close)
        assertEquals(LocalTime.of(20, 30), selection.notifyAt) // 1.5h before
    }

    @Test
    fun outdoorWinsWhenIndoorClosesEarlier() {
        val earlyCity = city.copy(
            openingHours = listOf(
                OpeningHourSlot(
                    days = listOf("mon"),
                    open = "06:00",
                    close = "18:00",
                ),
            ),
        )
        val date = LocalDate.of(2026, 8, 10)
        val selection = EveningPoolSelector.selectForDate(
            pools = listOf(earlyCity, outdoor),
            date = date,
            seasonYear = 2026,
        )
        assertNotNull(selection)
        assertEquals("allenmoos", selection!!.pool.id)
        assertEquals(LocalTime.of(21, 0), selection.close)
        assertEquals(LocalTime.of(19, 30), selection.notifyAt)
    }

    @Test
    fun skipsPastNotifyTimeWhenSelectingNext() {
        val date = LocalDate.of(2026, 8, 10)
        // City notify 20:30; now 21:00 → skip today
        val next = EveningPoolSelector.selectNext(
            pools = listOf(city, blasi),
            fromDate = date,
            nowTime = LocalTime.of(21, 0),
            seasonYear = 2026,
        )
        assertNotNull(next)
        assertTrue(next!!.date.isAfter(date))
        assertEquals("city", next.pool.id)
    }

    @Test
    fun noCandidateWhenAllCloseBeforeEvening() {
        val morningOnly = Pool(
            id = "kaeferberg",
            name = "Wärmebad Käferberg",
            kind = "waermebad",
            category = "indoor",
            openingHours = listOf(
                OpeningHourSlot(days = listOf("mon"), open = "11:00", close = "15:00"),
            ),
        )
        val selection = EveningPoolSelector.selectForDate(
            pools = listOf(morningOnly),
            date = LocalDate.of(2026, 8, 10),
        )
        assertNull(selection)
    }

    @Test
    fun seasonPeriodParserMayRange() {
        val range = SeasonPeriodParser.parse("30. Mai–16. August", 2026)
        assertNotNull(range)
        assertTrue(LocalDate.of(2026, 8, 10) in range!!)
        assertTrue(LocalDate.of(2026, 5, 30) in range)
        assertTrue(LocalDate.of(2026, 8, 16) in range)
        assertTrue(LocalDate.of(2026, 8, 17) !in range)
    }
}
