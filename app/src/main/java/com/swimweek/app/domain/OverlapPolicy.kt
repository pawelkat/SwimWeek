package com.swimweek.app.domain

/**
 * When two swim sessions overlap in time, credit distance once:
 * keep the longer session's distance (tie → earlier startTime); zero the other
 * and mark [SwimSession.overlapSuppressed].
 *
 * Pure domain helper — used by the Health Connect aggregator (PR 4).
 */
fun applyOverlapPolicy(sessions: List<SwimSession>): List<SwimSession> {
    if (sessions.size <= 1) return sessions

    val sorted = sessions.sortedWith(compareBy({ it.start }, { it.id }))
    val result = sorted.map { it }.toMutableList()
    val suppressed = BooleanArray(result.size)

    for (i in result.indices) {
        if (suppressed[i]) continue
        for (j in (i + 1) until result.size) {
            if (suppressed[j]) continue
            if (!overlaps(result[i], result[j])) continue

            val a = result[i]
            val b = result[j]
            val aDuration = a.end.toEpochMilli() - a.start.toEpochMilli()
            val bDuration = b.end.toEpochMilli() - b.start.toEpochMilli()

            val suppressIndex = when {
                aDuration > bDuration -> j
                bDuration > aDuration -> i
                // tie: earlier start keeps distance; if same start, lower id keeps it
                a.start.isBefore(b.start) -> j
                b.start.isBefore(a.start) -> i
                a.id <= b.id -> j
                else -> i
            }

            val keeperIndex = if (suppressIndex == i) j else i
            // If keeper already has zero and suppressed has distance, still only one credit —
            // prefer non-zero when choosing (duration already primary).
            if (result[keeperIndex].distanceMeters == 0.0 &&
                result[suppressIndex].distanceMeters > 0.0 &&
                aDuration == bDuration
            ) {
                // Same duration: keep the one with distance
                val realSuppress = keeperIndex
                val realKeeper = suppressIndex
                result[realSuppress] = result[realSuppress].copy(
                    distanceMeters = 0.0,
                    distanceSource = DistanceSource.NONE,
                    partialDistance = true,
                    overlapSuppressed = true,
                )
                suppressed[realSuppress] = true
                continue
            }

            result[suppressIndex] = result[suppressIndex].copy(
                distanceMeters = 0.0,
                distanceSource = DistanceSource.NONE,
                partialDistance = true,
                overlapSuppressed = true,
            )
            suppressed[suppressIndex] = true
            if (suppressIndex == i) break
        }
    }

    return result
}

private fun overlaps(a: SwimSession, b: SwimSession): Boolean {
    // [start, end) intervals
    return a.start.isBefore(b.end) && b.start.isBefore(a.end)
}
