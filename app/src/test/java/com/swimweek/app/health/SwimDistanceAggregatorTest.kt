package com.swimweek.app.health

import com.swimweek.app.domain.DistanceSource
import com.swimweek.app.domain.SourceStatus
import com.swimweek.app.domain.SwimType
import com.swimweek.app.domain.WeekRange
import com.swimweek.app.util.FixedClockProvider
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwimDistanceAggregatorTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val week = WeekRange.of(LocalDate.of(2026, 7, 22), zone, DayOfWeek.MONDAY)
    // Week: Mon 2026-07-20 00:00 → Mon 2026-07-27 00:00 Paris
    private val clock = FixedClockProvider(
        instant = ZonedDateTime.of(2026, 7, 22, 12, 0, 0, 0, zone).toInstant(),
        zoneId = zone,
    )

    private fun instant(day: Int, hour: Int, minute: Int = 0): Instant =
        ZonedDateTime.of(2026, 7, day, hour, minute, 0, 0, zone).toInstant()

    @Test
    fun aggregatesOriginFilteredDistance() = runBlocking {
        val start = instant(21, 10)
        val end = instant(21, 11)
        val fake = FakeHealthConnectDataSource(
            sessions = listOf(
                RawExerciseSession(
                    id = "s1",
                    swimType = SwimType.POOL,
                    start = start,
                    end = end,
                    dataOriginPackage = "com.sec.android.app.shealth",
                    title = "Pool",
                    lapLengthMeters = 0.0,
                ),
            ),
            distanceByWindowAndOrigin = mapOf(
                Triple(start, end, "com.sec.android.app.shealth") to 1500.0,
                // Contaminating origin in same window — must be ignored
                Triple(start, end, "com.other.fitness") to 9999.0,
            ),
            unscopedDistanceByWindow = mapOf((start to end) to 11499.0),
        )
        val summary = SwimDistanceAggregator(fake, clock).aggregateWeek(week)
        assertEquals(1500.0, summary.totalDistanceMeters, 0.0)
        assertEquals(1, summary.sessionCount)
        assertEquals(DistanceSource.AGGREGATE_ORIGIN_FILTERED, summary.sessions.single().distanceSource)
        assertEquals(SourceStatus.OK, summary.sourceStatus)
    }

    @Test
    fun ignoresNonSwimExerciseTypes() = runBlocking {
        val start = instant(21, 10)
        val end = instant(21, 11)
        val fake = FakeHealthConnectDataSource(
            sessions = listOf(
                RawExerciseSession(
                    id = "run",
                    swimType = null,
                    start = start,
                    end = end,
                    dataOriginPackage = "com.sec.android.app.shealth",
                    title = "Run",
                    lapLengthMeters = 0.0,
                ),
            ),
            distanceByWindowAndOrigin = mapOf(
                Triple(start, end, "com.sec.android.app.shealth") to 5000.0,
            ),
        )
        val summary = SwimDistanceAggregator(fake, clock).aggregateWeek(week)
        assertEquals(0, summary.sessionCount)
        assertEquals(0.0, summary.totalDistanceMeters, 0.0)
        assertEquals(SourceStatus.NO_DATA, summary.sourceStatus)
    }

    @Test
    fun lapFallbackWhenAggregateZero() = runBlocking {
        val start = instant(22, 9)
        val end = instant(22, 10)
        val fake = FakeHealthConnectDataSource(
            sessions = listOf(
                RawExerciseSession(
                    id = "laps",
                    swimType = SwimType.POOL,
                    start = start,
                    end = end,
                    dataOriginPackage = "com.sec.android.app.shealth",
                    title = null,
                    lapLengthMeters = 2000.0,
                ),
            ),
            distanceByWindowAndOrigin = emptyMap(),
        )
        val summary = SwimDistanceAggregator(fake, clock).aggregateWeek(week)
        assertEquals(2000.0, summary.totalDistanceMeters, 0.0)
        assertEquals(DistanceSource.LAP_LENGTHS, summary.sessions.single().distanceSource)
        assertEquals(false, summary.sessions.single().partialDistance)
    }

    @Test
    fun zeroDistanceStillCountsAsPartialSession() = runBlocking {
        val start = instant(22, 9)
        val end = instant(22, 10)
        val fake = FakeHealthConnectDataSource(
            sessions = listOf(
                RawExerciseSession(
                    id = "empty",
                    swimType = SwimType.OPEN_WATER,
                    start = start,
                    end = end,
                    dataOriginPackage = "com.sec.android.app.shealth",
                    title = null,
                    lapLengthMeters = 0.0,
                ),
            ),
        )
        val summary = SwimDistanceAggregator(fake, clock).aggregateWeek(week)
        assertEquals(1, summary.sessionCount)
        assertEquals(0, summary.sessionsWithDistanceCount)
        assertEquals(1, summary.partialDistanceSessionCount)
        assertEquals(0.0, summary.totalDistanceMeters, 0.0)
        assertEquals(DistanceSource.NONE, summary.sessions.single().distanceSource)
        assertTrue(summary.sessions.single().partialDistance)
        assertEquals(SourceStatus.OK, summary.sourceStatus) // has sessions
    }

    @Test
    fun sundayNightSession_notInMondayWeek() = runBlocking {
        // Session starts Sunday 23:30 before week start Monday 20th
        val start = ZonedDateTime.of(2026, 7, 19, 23, 30, 0, 0, zone).toInstant()
        val end = ZonedDateTime.of(2026, 7, 20, 0, 20, 0, 0, zone).toInstant()
        val fake = FakeHealthConnectDataSource(
            sessions = listOf(
                RawExerciseSession(
                    id = "cross",
                    swimType = SwimType.POOL,
                    start = start,
                    end = end,
                    dataOriginPackage = "com.sec.android.app.shealth",
                    title = null,
                    lapLengthMeters = 0.0,
                ),
            ),
            distanceByWindowAndOrigin = mapOf(
                Triple(start, end, "com.sec.android.app.shealth") to 1000.0,
            ),
        )
        val summary = SwimDistanceAggregator(fake, clock).aggregateWeek(week)
        assertEquals(0, summary.sessionCount)
        assertEquals(0.0, summary.totalDistanceMeters, 0.0)
    }

    @Test
    fun mondayMorningSession_inWeek() = runBlocking {
        val start = instant(20, 6)
        val end = instant(20, 7)
        val fake = FakeHealthConnectDataSource(
            sessions = listOf(
                RawExerciseSession(
                    id = "mon",
                    swimType = SwimType.POOL,
                    start = start,
                    end = end,
                    dataOriginPackage = "com.sec.android.app.shealth",
                    title = null,
                    lapLengthMeters = 0.0,
                ),
            ),
            distanceByWindowAndOrigin = mapOf(
                Triple(start, end, "com.sec.android.app.shealth") to 750.0,
            ),
        )
        val summary = SwimDistanceAggregator(fake, clock).aggregateWeek(week)
        assertEquals(1, summary.sessionCount)
        assertEquals(750.0, summary.totalDistanceMeters, 0.0)
    }

    @Test
    fun overlappingSessions_creditDistanceOnce() = runBlocking {
        val aStart = instant(21, 10)
        val aEnd = instant(21, 11)
        val bStart = instant(21, 10, 30)
        val bEnd = instant(21, 12) // longer
        val fake = FakeHealthConnectDataSource(
            sessions = listOf(
                RawExerciseSession(
                    id = "a",
                    swimType = SwimType.POOL,
                    start = aStart,
                    end = aEnd,
                    dataOriginPackage = "com.sec.android.app.shealth",
                    title = null,
                    lapLengthMeters = 0.0,
                ),
                RawExerciseSession(
                    id = "b",
                    swimType = SwimType.POOL,
                    start = bStart,
                    end = bEnd,
                    dataOriginPackage = "com.sec.android.app.shealth",
                    title = null,
                    lapLengthMeters = 0.0,
                ),
            ),
            distanceByWindowAndOrigin = mapOf(
                Triple(aStart, aEnd, "com.sec.android.app.shealth") to 800.0,
                Triple(bStart, bEnd, "com.sec.android.app.shealth") to 1200.0,
            ),
        )
        val summary = SwimDistanceAggregator(fake, clock).aggregateWeek(week)
        assertEquals(2, summary.sessionCount)
        assertEquals(1200.0, summary.totalDistanceMeters, 0.0)
        assertTrue(summary.sessions.any { it.overlapSuppressed })
    }
}
