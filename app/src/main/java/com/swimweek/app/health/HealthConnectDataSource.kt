package com.swimweek.app.health

import com.swimweek.app.domain.SwimType
import java.time.Instant

/**
 * Abstraction over Health Connect exercise/distance reads so aggregation
 * can be unit-tested without a real [androidx.health.connect.client.HealthConnectClient].
 */
interface HealthConnectDataSource {
    /**
     * Exercise sessions that may overlap [start, endExclusive). Caller post-filters by startTime.
     */
    suspend fun listExerciseSessions(start: Instant, endExclusive: Instant): List<RawExerciseSession>

    /**
     * DISTANCE_TOTAL for [start, end) optionally filtered to [dataOriginPackage].
     * When [dataOriginPackage] is null/blank, origin filter is omitted (degraded path).
     */
    suspend fun distanceTotalMeters(
        start: Instant,
        end: Instant,
        dataOriginPackage: String?,
    ): Double
}

data class RawExerciseSession(
    val id: String,
    val swimType: SwimType?,
    val start: Instant,
    val end: Instant,
    val dataOriginPackage: String,
    val title: String?,
    /** Precomputed sum of lap lengths in meters (0 if none). */
    val lapLengthMeters: Double,
)
