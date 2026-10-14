package com.swimweek.app.domain

import java.time.Duration
import java.time.Instant

/** How a swim that did not come from Health Connect was entered. */
enum class ManualSwimKind {
    /** Pool swim, in a Zurich pool or one the user added. */
    POOL,

    /** Open-water swim. The name is the place, such as a lake or river. */
    OPEN_WATER,
}

/**
 * Maps a stored kind string, including values from the first log-swim build.
 * WATCH_FAIL was a third kind. Those rows load as pool swims with the watch-missed flag.
 */
fun decodeManualKind(raw: String): ManualSwimKind? =
    when (raw) {
        "OPEN_WATER" -> ManualSwimKind.OPEN_WATER
        "POOL", "ARBITRARY_POOL", "WATCH_FAIL" -> ManualSwimKind.POOL
        else -> null
    }

fun decodeWatchMissed(rawKind: String, storedFlag: Boolean): Boolean =
    storedFlag || rawKind == "WATCH_FAIL"

/**
 * One swim the user logged on this phone.
 * [id] always starts with [MANUAL_ID_PREFIX]. The end is [start] plus [MANUAL_WINDOW]
 * so a later Health Connect session of the same swim can be detected as an overlap.
 */
data class ManualSwim(
    val id: String,
    val kind: ManualSwimKind,
    val name: String,
    val start: Instant,
    val distanceMeters: Double,
    /** Zurich or custom pool id when [kind] is [ManualSwimKind.POOL]. */
    val poolId: String? = null,
    /** The user marked that the watch did not record this swim. Does not change the total. */
    val watchMissed: Boolean = false,
) {
    val end: Instant get() = start.plus(MANUAL_WINDOW)

    init {
        require(id.startsWith(MANUAL_ID_PREFIX)) { "manual id must start with $MANUAL_ID_PREFIX" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(distanceMeters > 0.0) { "distanceMeters must be positive" }
    }

    fun toSession(): SwimSession =
        SwimSession(
            id = id,
            exerciseType = if (kind == ManualSwimKind.OPEN_WATER) {
                SwimType.OPEN_WATER
            } else {
                SwimType.POOL
            },
            start = start,
            end = end,
            distanceMeters = distanceMeters,
            distanceSource = DistanceSource.MANUAL,
            partialDistance = false,
            dataOriginPackage = MANUAL_ORIGIN_PACKAGE,
            title = name,
        )
}

const val MANUAL_ID_PREFIX = "manual-"
const val MANUAL_ORIGIN_PACKAGE = "com.swimweek.app"

/** Window used only to detect a later watch import of the same swim. */
val MANUAL_WINDOW: Duration = Duration.ofMinutes(45)

fun SwimSession.isManualEntry(): Boolean =
    distanceSource == DistanceSource.MANUAL || id.startsWith(MANUAL_ID_PREFIX)

/**
 * Pool chosen for a Health Connect session, or the name typed for a manual swim.
 * Health Connect rows with no pool stay unset so the home row can ask for one.
 */
fun SwimSession.resolvedPlaceName(savedPoolName: String?): String? {
    if (!savedPoolName.isNullOrBlank()) return savedPoolName
    if (isManualEntry()) return title?.takeIf { it.isNotBlank() }
    return null
}

/**
 * Credit each swim once.
 *
 * Health Connect sessions are left as the aggregator produced them.
 * A manual swim that overlaps a credited Health Connect session (distance above 0,
 * not already overlap-suppressed) is kept on the list with distance 0 so a later
 * watch import is not double-counted. Manual swims that do not overlap Health
 * Connect still overlap-suppress each other.
 * Manual rows already present in [healthSessions] are dropped before the merge.
 */
fun mergeManualSessions(
    healthSessions: List<SwimSession>,
    manuals: List<ManualSwim>,
    week: WeekRange,
): List<SwimSession> {
    val health = healthSessions.filterNot { it.isManualEntry() }
    val credited = health.filter { !it.overlapSuppressed && it.distanceMeters > 0.0 }
    val suppressedByHealth = mutableListOf<SwimSession>()
    val free = mutableListOf<SwimSession>()
    for (manual in manuals) {
        if (!week.containsSessionStart(manual.start)) continue
        val session = manual.toSession()
        val overlapsCredited = credited.any { healthSession ->
            session.start.isBefore(healthSession.end) && healthSession.start.isBefore(session.end)
        }
        if (overlapsCredited) {
            suppressedByHealth += session.copy(
                distanceMeters = 0.0,
                partialDistance = true,
                overlapSuppressed = true,
            )
        } else {
            free += session
        }
    }
    val amongManuals = applyOverlapPolicy(free).map { session ->
        if (session.isManualEntry() && session.overlapSuppressed) {
            session.copy(distanceSource = DistanceSource.MANUAL)
        } else {
            session
        }
    }
    return (health + amongManuals + suppressedByHealth).sortedByDescending { it.start }
}

/** Recompute totals from [sessions]. Leaves [WeeklySwimSummary.sourceStatus] unchanged. */
fun WeeklySwimSummary.withMergedSessions(sessions: List<SwimSession>): WeeklySwimSummary {
    val sorted = sessions.sortedByDescending { it.start }
    return copy(
        totalDistanceMeters = sorted.sumOf { it.distanceMeters },
        sessionCount = sorted.size,
        sessionsWithDistanceCount = sorted.count { it.distanceMeters > 0.0 },
        partialDistanceSessionCount = sorted.count { it.partialDistance },
        sessions = sorted,
    )
}
