package com.swimweek.app.health

import com.swimweek.app.domain.DistanceSource
import com.swimweek.app.domain.SourceStatus
import com.swimweek.app.domain.SwimSession
import com.swimweek.app.domain.WeekRange
import com.swimweek.app.domain.WeeklySwimSummary
import com.swimweek.app.domain.applyOverlapPolicy
import com.swimweek.app.util.ClockProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production weekly swim aggregation (PR 4).
 *
 * Session isolation → origin-filtered distance → lap fallback → overlap credit-once.
 */
@Singleton
class SwimDistanceAggregator @Inject constructor(
    private val dataSource: HealthConnectDataSource,
    private val clock: ClockProvider,
) {
    suspend fun aggregateWeek(week: WeekRange): WeeklySwimSummary {
        val rawRecords = dataSource.listExerciseSessions(week.start, week.endExclusive)
        val rawSessions = mutableListOf<SwimSession>()

        for (rec in rawRecords) {
            val swimType = rec.swimType ?: continue
            // Week attribution: entire session → week containing startTime
            if (!week.containsSessionStart(rec.start)) continue

            val origin = rec.dataOriginPackage
            val aggregateMeters = dataSource.distanceTotalMeters(
                start = rec.start,
                end = rec.end,
                dataOriginPackage = origin.ifBlank { null },
            )
            val lapMeters = rec.lapLengthMeters.coerceAtLeast(0.0)

            val (meters, source) = when {
                aggregateMeters > 0.0 ->
                    aggregateMeters to DistanceSource.AGGREGATE_ORIGIN_FILTERED
                lapMeters > 0.0 ->
                    lapMeters to DistanceSource.LAP_LENGTHS
                else ->
                    0.0 to DistanceSource.NONE
            }

            rawSessions += SwimSession(
                id = rec.id,
                exerciseType = swimType,
                start = rec.start,
                end = rec.end,
                distanceMeters = meters,
                distanceSource = source,
                partialDistance = meters == 0.0,
                dataOriginPackage = origin,
                title = rec.title,
            )
        }

        val sessions = applyOverlapPolicy(rawSessions)
            .sortedByDescending { it.start }

        return WeeklySwimSummary(
            week = week,
            totalDistanceMeters = sessions.sumOf { it.distanceMeters },
            sessionCount = sessions.size,
            sessionsWithDistanceCount = sessions.count { it.distanceMeters > 0.0 },
            partialDistanceSessionCount = sessions.count { it.partialDistance },
            sessions = sessions,
            lastSyncedAt = clock.now(),
            sourceStatus = if (sessions.isEmpty()) SourceStatus.NO_DATA else SourceStatus.OK,
        )
    }
}
