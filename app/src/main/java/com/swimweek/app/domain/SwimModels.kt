package com.swimweek.app.domain

import java.time.Instant

enum class SwimType {
    POOL,
    OPEN_WATER,
}

/**
 * How [SwimSession.distanceMeters] was resolved during aggregation.
 */
enum class DistanceSource {
    /** Health Connect DISTANCE_TOTAL with dataOriginFilter = session origin. */
    AGGREGATE_ORIGIN_FILTERED,

    /** Sum of ExerciseLap.length when aggregate was zero. */
    LAP_LENGTHS,

    /** No usable distance after all fallbacks. */
    NONE,
}

enum class SourceStatus {
    OK,
    HEALTH_CONNECT_UNAVAILABLE,
    PERMISSIONS_MISSING,
    NO_DATA,

    /**
     * User manually reported missing data from Diagnostics while HC returned
     * no swim sessions. Not set by automatic heuristics in v1.
     */
    USER_REPORTED_MISSING_BRIDGE,

    ERROR,
}

/**
 * One swimming exercise session after domain resolution (no Health Connect types).
 */
data class SwimSession(
    val id: String,
    val exerciseType: SwimType,
    val start: Instant,
    val end: Instant,
    val distanceMeters: Double,
    val distanceSource: DistanceSource,
    val partialDistance: Boolean,
    val dataOriginPackage: String,
    val title: String? = null,
    /** True when distance was zeroed due to overlap with another session. */
    val overlapSuppressed: Boolean = false,
) {
    init {
        require(distanceMeters >= 0.0) { "distanceMeters must be non-negative" }
        require(!end.isBefore(start)) { "end must be >= start" }
    }
}

/**
 * Derived weekly total shown on widget / home. On-device only.
 */
data class WeeklySwimSummary(
    val week: WeekRange,
    val totalDistanceMeters: Double,
    val sessionCount: Int,
    val sessionsWithDistanceCount: Int,
    val partialDistanceSessionCount: Int,
    val sessions: List<SwimSession>,
    val lastSyncedAt: Instant,
    val sourceStatus: SourceStatus,
    val userReportedMissingData: Boolean = false,
) {
    init {
        require(totalDistanceMeters >= 0.0)
        require(sessionCount >= 0)
        require(sessionsWithDistanceCount >= 0)
        require(partialDistanceSessionCount >= 0)
    }

    companion object {
        fun empty(
            week: WeekRange,
            lastSyncedAt: Instant,
            sourceStatus: SourceStatus = SourceStatus.NO_DATA,
        ): WeeklySwimSummary =
            WeeklySwimSummary(
                week = week,
                totalDistanceMeters = 0.0,
                sessionCount = 0,
                sessionsWithDistanceCount = 0,
                partialDistanceSessionCount = 0,
                sessions = emptyList(),
                lastSyncedAt = lastSyncedAt,
                sourceStatus = sourceStatus,
            )
    }
}
