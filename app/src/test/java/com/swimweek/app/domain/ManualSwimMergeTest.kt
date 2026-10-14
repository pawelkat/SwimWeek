package com.swimweek.app.domain

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualSwimMergeTest {

    private val zone = ZoneId.of("Europe/Zurich")
    private val week = WeekRange.of(LocalDate.of(2026, 10, 12), zone)

    private fun atHour(hour: Long): java.time.Instant = week.start.plus(Duration.ofHours(hour))

    private fun health(
        id: String,
        startHour: Long,
        endHour: Long,
        meters: Double,
        suppressed: Boolean = false,
    ): SwimSession =
        SwimSession(
            id = id,
            exerciseType = SwimType.POOL,
            start = atHour(startHour),
            end = atHour(endHour),
            distanceMeters = if (suppressed) 0.0 else meters,
            distanceSource = if (suppressed) DistanceSource.NONE else DistanceSource.AGGREGATE_ORIGIN_FILTERED,
            partialDistance = suppressed || meters == 0.0,
            dataOriginPackage = "com.sec.android.app.shealth",
            overlapSuppressed = suppressed,
        )

    private fun manual(
        id: String,
        startHour: Long,
        meters: Double,
        kind: ManualSwimKind = ManualSwimKind.POOL,
        name: String = "Hotel pool",
    ): ManualSwim =
        ManualSwim(
            id = id,
            kind = kind,
            name = name,
            start = atHour(startHour),
            distanceMeters = meters,
        )

    @Test
    fun manual_that_does_not_overlap_is_counted() {
        val hc = health("hc", 10, 11, 2000.0)
        val entered = manual("manual-ow", 14, 800.0, ManualSwimKind.OPEN_WATER, "Lake")
        val out = mergeManualSessions(listOf(hc), listOf(entered), week)
        assertEquals(2000.0, out.single { it.id == "hc" }.distanceMeters, 0.0)
        val lake = out.single { it.id == "manual-ow" }
        assertEquals(800.0, lake.distanceMeters, 0.0)
        assertEquals(SwimType.OPEN_WATER, lake.exerciseType)
        assertEquals(DistanceSource.MANUAL, lake.distanceSource)
        assertEquals(MANUAL_ORIGIN_PACKAGE, lake.dataOriginPackage)
        assertEquals("Lake", lake.title)
        assertFalse(lake.overlapSuppressed)
        val summary = WeeklySwimSummary.empty(week, week.start).withMergedSessions(out)
        assertEquals(2800.0, summary.totalDistanceMeters, 0.0)
        assertEquals(2, summary.sessionCount)
    }

    @Test
    fun manual_overlapping_credited_health_session_is_zeroed() {
        val hc = health("hc", 10, 11, 2000.0)
        val entered = manual("manual-watch", 10, 1500.0, ManualSwimKind.POOL, "Watch missed")
        val out = mergeManualSessions(listOf(hc), listOf(entered), week)
        assertEquals(2000.0, out.single { it.id == "hc" }.distanceMeters, 0.0)
        val zeroed = out.single { it.id == "manual-watch" }
        assertEquals(0.0, zeroed.distanceMeters, 0.0)
        assertTrue(zeroed.overlapSuppressed)
        assertEquals(DistanceSource.MANUAL, zeroed.distanceSource)
        assertEquals(2000.0, out.sumOf { it.distanceMeters }, 0.0)
    }

    @Test
    fun manual_overlapping_zero_distance_health_session_is_counted() {
        val hc = health("hc", 10, 11, 0.0)
        val entered = manual("manual-watch", 10, 1500.0, ManualSwimKind.POOL, "Watch missed")
        val out = mergeManualSessions(listOf(hc), listOf(entered), week)
        assertEquals(1500.0, out.single { it.id == "manual-watch" }.distanceMeters, 0.0)
        assertFalse(out.single { it.id == "manual-watch" }.overlapSuppressed)
    }

    @Test
    fun manual_that_starts_when_health_session_ends_is_counted() {
        val hc = health("hc", 10, 11, 2000.0)
        val entered = manual("manual-next", 11, 400.0)
        val out = mergeManualSessions(listOf(hc), listOf(entered), week)
        assertEquals(400.0, out.single { it.id == "manual-next" }.distanceMeters, 0.0)
    }

    @Test
    fun manual_outside_the_week_is_dropped() {
        val previous = manual("manual-old", 0, 500.0).copy(
            start = week.previous().start.plus(Duration.ofHours(10)),
        )
        val out = mergeManualSessions(emptyList(), listOf(previous), week)
        assertTrue(out.isEmpty())
    }

    @Test
    fun empty_health_week_still_counts_manual() {
        val entered = manual("manual-pool", 18, 1200.0)
        val out = mergeManualSessions(emptyList(), listOf(entered), week)
        assertEquals(1200.0, out.single().distanceMeters, 0.0)
        assertEquals(SwimType.POOL, out.single().exerciseType)
    }

    @Test
    fun manual_rows_already_on_the_health_list_are_not_double_counted() {
        val stale = manual("manual-stale", 14, 9999.0).toSession()
        val hc = health("hc", 10, 11, 1000.0)
        val fresh = manual("manual-fresh", 14, 500.0)
        val out = mergeManualSessions(listOf(hc, stale), listOf(fresh), week)
        assertEquals(listOf("hc", "manual-fresh").sorted(), out.map { it.id }.sorted())
        assertEquals(1500.0, out.sumOf { it.distanceMeters }, 0.0)
    }

    @Test
    fun stored_watch_fail_kind_loads_as_pool_with_flag() {
        assertEquals(ManualSwimKind.POOL, decodeManualKind("WATCH_FAIL"))
        assertEquals(ManualSwimKind.POOL, decodeManualKind("ARBITRARY_POOL"))
        assertEquals(ManualSwimKind.OPEN_WATER, decodeManualKind("OPEN_WATER"))
        assertTrue(decodeWatchMissed("WATCH_FAIL", storedFlag = false))
        assertFalse(decodeWatchMissed("POOL", storedFlag = false))
        assertTrue(decodeWatchMissed("POOL", storedFlag = true))
    }

    @Test
    fun overlapping_manuals_credit_the_earlier_one_only() {
        val first = manual("manual-a", 10, 1000.0, name = "A")
        val second = manual("manual-b", 10, 800.0, name = "B").copy(
            start = atHour(10).plus(Duration.ofMinutes(10)),
        )
        val out = mergeManualSessions(emptyList(), listOf(second, first), week)
        assertEquals(1000.0, out.single { it.id == "manual-a" }.distanceMeters, 0.0)
        val suppressed = out.single { it.id == "manual-b" }
        assertEquals(0.0, suppressed.distanceMeters, 0.0)
        assertTrue(suppressed.overlapSuppressed)
        assertEquals(DistanceSource.MANUAL, suppressed.distanceSource)
    }
}
