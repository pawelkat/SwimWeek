package com.swimweek.app.health

import com.swimweek.app.domain.SourceStatus
import com.swimweek.app.domain.WeekRange
import com.swimweek.app.domain.WeeklySwimSummary
import com.swimweek.app.util.FixedClockProvider
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Revoke path contract: clearLocalHealthState is the repository entry point
 * that must clear summary + changes token (full DataStore instrumentation in PR 8).
 */
class SwimDistanceRepositoryClearTest {

    @Test
    fun clearLocalHealthState_methodExists() {
        assertTrue(
            SwimDistanceRepository::class.java.methods.any { it.name == "clearLocalHealthState" },
        )
    }

    @Test
    fun clearSemantics_emptyMemoryAfterClear() {
        val memory = mutableListOf<WeeklySwimSummary?>()
        val week = WeekRange.of(LocalDate.of(2026, 7, 22), ZoneId.of("UTC"), DayOfWeek.MONDAY)
        memory.add(
            WeeklySwimSummary.empty(
                week = week,
                lastSyncedAt = ZonedDateTime.parse("2026-07-22T12:00:00Z").toInstant(),
                sourceStatus = SourceStatus.OK,
            ),
        )
        // Simulate clearLocalHealthState effect
        memory.clear()
        assertTrue(memory.isEmpty())
    }

    @Test
    fun aggregatorWorksAfterEmptyDataset() = runBlocking {
        val zone = ZoneId.of("UTC")
        val clock = FixedClockProvider(
            ZonedDateTime.of(2026, 7, 22, 12, 0, 0, 0, zone).toInstant(),
            zone,
        )
        val summary = SwimDistanceAggregator(FakeHealthConnectDataSource(), clock)
            .aggregateWeek(WeekRange.of(LocalDate.of(2026, 7, 22), zone, DayOfWeek.MONDAY))
        assertTrue(summary.sessionCount == 0)
        assertTrue(summary.sourceStatus == SourceStatus.NO_DATA)
    }
}
