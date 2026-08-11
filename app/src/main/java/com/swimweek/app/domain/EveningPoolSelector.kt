package com.swimweek.app.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * Picks the pool that stays open longest into the evening for a given date.
 *
 * Scoring (in order):
 * 1. Latest closing time (primary: “open longest on the evening”)
 * 2. Longest evening window from [eveningStart] to close
 * 3. Prefer indoor (hallenbad / waermebad) over outdoor / school pools
 * 4. Stable name sort
 */
object EveningPoolSelector {

    val DEFAULT_EVENING_START: LocalTime = LocalTime.of(17, 0)

    /** Minutes before close to fire the reminder. */
    const val DEFAULT_LEAD_MINUTES: Long = 90

    data class Candidate(
        val pool: Pool,
        val open: LocalTime,
        val close: LocalTime,
        val condition: String?,
        val audience: String?,
    ) {
        fun eveningDuration(eveningStart: LocalTime): Duration {
            val start = maxOf(open, eveningStart)
            return if (close.isAfter(start)) Duration.between(start, close) else Duration.ZERO
        }
    }

    data class Selection(
        val candidate: Candidate,
        val date: LocalDate,
        val notifyAt: LocalTime,
        val leadMinutes: Long,
    ) {
        val pool: Pool get() = candidate.pool
        val close: LocalTime get() = candidate.close
    }

    fun selectForDate(
        pools: List<Pool>,
        date: LocalDate,
        seasonYear: Int? = date.year,
        eveningStart: LocalTime = DEFAULT_EVENING_START,
        leadMinutes: Long = DEFAULT_LEAD_MINUTES,
    ): Selection? {
        val dayCode = dayCode(date.dayOfWeek)
        val candidates = pools
            .asSequence()
            .filter { it.selectable }
            .flatMap { pool -> windowsForDay(pool, date, dayCode, seasonYear) }
            .filter { it.close.isAfter(eveningStart) }
            .filter { it.close.isAfter(it.open) }
            .toList()

        val best = candidates.maxWithOrNull(candidateComparator(eveningStart)) ?: return null
        // LocalTime.minusMinutes wraps past midnight; clamp to midnight if lead > time since 00:00
        val minutesSinceMidnight = best.close.toSecondOfDay() / 60L
        val notifyAt = if (minutesSinceMidnight > leadMinutes) {
            best.close.minusMinutes(leadMinutes)
        } else {
            LocalTime.MIDNIGHT
        }
        return Selection(
            candidate = best,
            date = date,
            notifyAt = notifyAt,
            leadMinutes = leadMinutes,
        )
    }

    /**
     * Find the next selection on or after [fromDate] where [notifyAt] is still in the future
     * relative to [nowTime] on that calendar day (caller supplies "now" for today).
     *
     * @param nowDateTime if non-null and date equals selection date, require notifyAt > now time
     */
    fun selectNext(
        pools: List<Pool>,
        fromDate: LocalDate,
        nowTime: LocalTime,
        seasonYear: Int? = fromDate.year,
        eveningStart: LocalTime = DEFAULT_EVENING_START,
        leadMinutes: Long = DEFAULT_LEAD_MINUTES,
        maxDaysAhead: Int = 14,
    ): Selection? {
        for (offset in 0..maxDaysAhead) {
            val date = fromDate.plusDays(offset.toLong())
            val selection = selectForDate(pools, date, seasonYear, eveningStart, leadMinutes)
                ?: continue
            if (offset == 0 && !selection.notifyAt.isAfter(nowTime)) {
                continue
            }
            return selection
        }
        return null
    }

    /**
     * All opening windows that apply on [date] (day-of-week + seasonal period).
     * When a pool has multiple overlapping slots, each is returned separately.
     */
    fun windowsForDate(
        pools: List<Pool>,
        date: LocalDate,
        seasonYear: Int? = date.year,
    ): List<Candidate> {
        val dayCode = dayCode(date.dayOfWeek)
        return pools
            .asSequence()
            .filter { it.selectable }
            .flatMap { pool -> windowsForDay(pool, date, dayCode, seasonYear) }
            .filter { it.close.isAfter(it.open) }
            .toList()
    }

    /**
     * Best window per pool for [date] (latest close among that pool's slots).
     */
    fun bestWindowPerPool(
        pools: List<Pool>,
        date: LocalDate,
        seasonYear: Int? = date.year,
    ): List<Candidate> =
        windowsForDate(pools, date, seasonYear)
            .groupBy { it.pool.id }
            .mapNotNull { (_, windows) -> windows.maxByOrNull { it.close } }

    fun windowsForDay(
        pool: Pool,
        date: LocalDate,
        dayCode: String,
        seasonYear: Int?,
    ): Sequence<Candidate> = sequence {
        for (slot in pool.openingHours) {
            val open = parseHm(slot.open) ?: continue
            val close = parseHm(slot.close) ?: continue
            if (!appliesOnDay(slot, dayCode)) continue
            if (!SeasonPeriodParser.contains(slot.period, date, seasonYear)) continue
            yield(
                Candidate(
                    pool = pool,
                    open = open,
                    close = close,
                    condition = slot.condition,
                    audience = slot.audience,
                ),
            )
        }
    }

    fun appliesOnDay(slot: OpeningHourSlot, dayCode: String): Boolean {
        val days = slot.days
        // null days: seasonal outdoor ranges apply every day within period
        if (days == null || days.isEmpty()) return true
        return days.any { it.equals(dayCode, ignoreCase = true) }
    }

    /** Natural order for [maxWithOrNull]: later close / longer evening / higher category wins. */
    private fun candidateComparator(eveningStart: LocalTime): Comparator<Candidate> =
        Comparator { a, b ->
            val closeCmp = a.close.compareTo(b.close)
            if (closeCmp != 0) return@Comparator closeCmp
            val durCmp = a.eveningDuration(eveningStart).compareTo(b.eveningDuration(eveningStart))
            if (durCmp != 0) return@Comparator durCmp
            val catCmp = categoryRank(a.pool).compareTo(categoryRank(b.pool))
            if (catCmp != 0) return@Comparator catCmp
            // Stable: alphabetical name when fully tied
            a.pool.name.compareTo(b.pool.name)
        }

    private fun categoryRank(pool: Pool): Int =
        when (pool.kind) {
            "hallenbad" -> 4
            "waermebad" -> 3
            "freibad", "seebad", "flussbad" -> 2
            "schulschwimmanlage" -> 1
            else -> 0
        }

    fun dayCode(day: DayOfWeek): String =
        when (day) {
            DayOfWeek.MONDAY -> "mon"
            DayOfWeek.TUESDAY -> "tue"
            DayOfWeek.WEDNESDAY -> "wed"
            DayOfWeek.THURSDAY -> "thu"
            DayOfWeek.FRIDAY -> "fri"
            DayOfWeek.SATURDAY -> "sat"
            DayOfWeek.SUNDAY -> "sun"
        }

    fun parseHm(text: String?): LocalTime? {
        if (text.isNullOrBlank() || text == "null") return null
        val m = Regex("""(\d{1,2}):(\d{2})""").find(text.trim()) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return LocalTime.of(h, min)
    }
}
