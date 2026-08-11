package com.swimweek.app.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenPoolRecommenderTest {

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

    private val date = LocalDate.of(2026, 8, 10) // Monday

    @Test
    fun recommendsOpenPoolsWithEnoughTime() {
        val now = LocalTime.of(18, 0)
        val list = OpenPoolRecommender.recommend(
            pools = listOf(city, blasi),
            date = date,
            now = now,
            minMinutesUntilClose = 90,
        )
        // City: 4h left → recommended; Bläsi: 1h left → closing soon
        val cityRec = list.first { it.pool.id == "city" }
        val blasiRec = list.first { it.pool.id == "blaesi" }
        assertTrue(cityRec.isRecommended)
        assertFalse(blasiRec.isRecommended)
        assertTrue(blasiRec.isClosingSoon)
        assertEquals(240, cityRec.minutesUntilClose)
        assertEquals(60, blasiRec.minutesUntilClose)
    }

    @Test
    fun favoritesSortFirst() {
        val now = LocalTime.of(12, 0)
        val list = OpenPoolRecommender.recommend(
            pools = listOf(city, blasi),
            date = date,
            now = now,
            favoriteIds = setOf("blaesi"),
            minMinutesUntilClose = 90,
        )
        assertEquals("blaesi", list.first().pool.id)
        assertTrue(list.first().isFavorite)
    }

    @Test
    fun topPickPrefersFavoriteRecommended() {
        val now = LocalTime.of(12, 0)
        val list = OpenPoolRecommender.recommend(
            pools = listOf(city, blasi),
            date = date,
            now = now,
            favoriteIds = setOf("blaesi"),
        )
        val top = OpenPoolRecommender.topPick(list)
        assertNotNull(top)
        assertEquals("blaesi", top!!.pool.id)
    }

    @Test
    fun closedPoolsExcluded() {
        val now = LocalTime.of(23, 0)
        val list = OpenPoolRecommender.recommend(
            pools = listOf(city, blasi),
            date = date,
            now = now,
        )
        assertTrue(list.isEmpty())
    }
}
