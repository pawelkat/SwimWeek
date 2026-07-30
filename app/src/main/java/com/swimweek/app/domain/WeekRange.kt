package com.swimweek.app.domain

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Inclusive start, exclusive end, interpreted in [zoneId].
 *
 * Week attribution rule (v1): a swim session belongs entirely to the week that
 * contains its [start] time — see [containsSessionStart].
 */
data class WeekRange(
    val start: Instant,
    val endExclusive: Instant,
    val zoneId: ZoneId,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
    init {
        require(start < endExclusive) {
            "WeekRange start ($start) must be before endExclusive ($endExclusive)"
        }
    }

    /** Stable identity for cache / week-rollover comparison. */
    fun identityKey(): String = "${start.toEpochMilli()}|${zoneId.id}|$weekStart"

    /**
     * True when a session with this [sessionStart] is attributed to this week
     * (`startTime ∈ [week.start, week.endExclusive)`).
     */
    fun containsSessionStart(sessionStart: Instant): Boolean =
        !sessionStart.isBefore(start) && sessionStart.isBefore(endExclusive)

    companion object {
        /**
         * Calendar week containing [clock]'s instant in [zoneId], starting on [weekStart]
         * at local midnight.
         */
        fun current(
            zoneId: ZoneId = ZoneId.systemDefault(),
            weekStart: DayOfWeek = DayOfWeek.MONDAY,
            clock: Clock = Clock.system(zoneId),
        ): WeekRange {
            val today = LocalDate.now(clock.withZone(zoneId))
            return of(today, zoneId, weekStart)
        }

        /**
         * Calendar week that contains [date] (local date in [zoneId]), starting on [weekStart].
         */
        fun of(
            date: LocalDate,
            zoneId: ZoneId = ZoneId.systemDefault(),
            weekStart: DayOfWeek = DayOfWeek.MONDAY,
        ): WeekRange {
            val startDate = date.with(TemporalAdjusters.previousOrSame(weekStart))
            val endDate = startDate.plusWeeks(1)
            val start = startDate.atStartOfDay(zoneId).toInstant()
            val endExclusive = endDate.atStartOfDay(zoneId).toInstant()
            return WeekRange(
                start = start,
                endExclusive = endExclusive,
                zoneId = zoneId,
                weekStart = weekStart,
            )
        }

        /**
         * Week that should own a session starting at [sessionStart]
         * (week containing startTime — never split across weeks).
         */
        fun forSessionStart(
            sessionStart: Instant,
            zoneId: ZoneId = ZoneId.systemDefault(),
            weekStart: DayOfWeek = DayOfWeek.MONDAY,
        ): WeekRange {
            val localDate = sessionStart.atZone(zoneId).toLocalDate()
            return of(localDate, zoneId, weekStart)
        }
    }
}
