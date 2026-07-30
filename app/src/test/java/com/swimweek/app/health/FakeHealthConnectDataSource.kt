package com.swimweek.app.health

import java.time.Instant

/**
 * In-memory Health Connect stub for JVM unit tests.
 *
 * [distanceByOrigin] keys are "startEpoch|endEpoch|package" → meters.
 * When package filter is applied, only that origin's meters are returned.
 * Non-matching origins simulate multi-app contamination isolation.
 */
class FakeHealthConnectDataSource(
    var sessions: List<RawExerciseSession> = emptyList(),
    /** Map of session window key → package → meters */
    var distanceByWindowAndOrigin: Map<Triple<Instant, Instant, String>, Double> = emptyMap(),
    /** Unscoped distance for a window (should NOT be used when origin filter present) */
    var unscopedDistanceByWindow: Map<Pair<Instant, Instant>, Double> = emptyMap(),
) : HealthConnectDataSource {

    override suspend fun listExerciseSessions(
        start: Instant,
        endExclusive: Instant,
    ): List<RawExerciseSession> {
        // HC may return overlap-matched sessions; return all that overlap the window
        return sessions.filter { it.start < endExclusive && it.end > start }
    }

    override suspend fun distanceTotalMeters(
        start: Instant,
        end: Instant,
        dataOriginPackage: String?,
    ): Double {
        if (dataOriginPackage.isNullOrBlank()) {
            return unscopedDistanceByWindow[start to end]
                ?: distanceByWindowAndOrigin
                    .filterKeys { it.first == start && it.second == end }
                    .values
                    .sum()
        }
        return distanceByWindowAndOrigin[Triple(start, end, dataOriginPackage)] ?: 0.0
    }
}
