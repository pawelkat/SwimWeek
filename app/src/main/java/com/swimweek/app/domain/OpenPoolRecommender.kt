package com.swimweek.app.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * Recommends pools that are open right now and not closing soon.
 */
object OpenPoolRecommender {

    /** Minimum minutes until close to count as a useful recommendation. */
    const val DEFAULT_MIN_MINUTES_UNTIL_CLOSE: Long = 90

    data class Recommendation(
        val pool: Pool,
        val open: LocalTime,
        val close: LocalTime,
        val minutesUntilClose: Long,
        val condition: String?,
        val audience: String?,
        val isFavorite: Boolean,
        /** True when open now and [minutesUntilClose] >= min threshold. */
        val isRecommended: Boolean,
        /** Open now but closes sooner than the threshold. */
        val isClosingSoon: Boolean,
    )

    /**
     * @param favoriteIds pool ids the user marked as favorites (sorted first).
     * @param includeClosingSoon when true, also list pools open now but < min minutes left.
     */
    fun recommend(
        pools: List<Pool>,
        date: LocalDate,
        now: LocalTime,
        favoriteIds: Set<String> = emptySet(),
        seasonYear: Int? = date.year,
        minMinutesUntilClose: Long = DEFAULT_MIN_MINUTES_UNTIL_CLOSE,
        includeClosingSoon: Boolean = true,
    ): List<Recommendation> {
        // One best open window per pool (latest close among currently open slots)
        val openNow = EveningPoolSelector.windowsForDate(pools, date, seasonYear)
            .filter { window ->
                !now.isBefore(window.open) && now.isBefore(window.close)
            }
            .groupBy { it.pool.id }
            .mapNotNull { (_, windows) -> windows.maxByOrNull { it.close } }
            .map { window ->
                val minutesLeft = Duration.between(now, window.close).toMinutes().coerceAtLeast(0)
                val recommended = minutesLeft >= minMinutesUntilClose
                Recommendation(
                    pool = window.pool,
                    open = window.open,
                    close = window.close,
                    minutesUntilClose = minutesLeft,
                    condition = window.condition,
                    audience = window.audience,
                    isFavorite = window.pool.id in favoriteIds,
                    isRecommended = recommended,
                    isClosingSoon = !recommended,
                )
            }
            .filter { includeClosingSoon || it.isRecommended }

        return openNow.sortedWith(
            compareByDescending<Recommendation> { it.isFavorite }
                .thenByDescending { it.isRecommended }
                .thenByDescending { it.minutesUntilClose }
                .thenBy { it.pool.name },
        )
    }

    /**
     * Top suggestion: favorite recommended first, else longest remaining open time.
     */
    fun topPick(recommendations: List<Recommendation>): Recommendation? =
        recommendations.firstOrNull { it.isRecommended && it.isFavorite }
            ?: recommendations.firstOrNull { it.isRecommended }
}
