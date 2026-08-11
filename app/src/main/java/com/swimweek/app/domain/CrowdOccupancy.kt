package com.swimweek.app.domain

/**
 * Live occupancy from Stadt Zürich CrowdMonitor (badi-public.crowdmonitor.ch).
 *
 * [level] matches city-site Auslastung icons: 1–4 filled person figures,
 * or null when data is invalid / unavailable.
 */
data class CrowdOccupancy(
    val uid: String,
    val name: String?,
    val currentFill: Double,
    val maxSpace: Double,
    val freeSpace: Double,
    val level: Int?,
    val valid: Boolean,
) {
    val guests: Int get() = currentFill.toInt()

    companion object {
        /**
         * Same formula as stadt-zuerich.ch crowdmonitor.html:
         * occupancy = min(floor(ratio * 100 / 25) + 1, 4)
         */
        fun levelFrom(currentFill: Double, maxSpace: Double): Int? {
            if (maxSpace <= 0.0 || currentFill < 0.0) return null
            val ratio = currentFill / maxSpace
            return minOf((kotlin.math.floor(ratio * 100.0 / 25.0) + 1).toInt(), 4)
                .coerceAtLeast(1)
        }

        fun fromRaw(
            uid: String,
            name: String?,
            currentFill: Double,
            maxSpace: Double,
            freeSpace: Double,
        ): CrowdOccupancy {
            val valid = maxSpace > 0.0 && currentFill >= 0.0
            return CrowdOccupancy(
                uid = uid,
                name = name,
                currentFill = currentFill,
                maxSpace = maxSpace,
                freeSpace = freeSpace,
                level = if (valid) levelFrom(currentFill, maxSpace) else null,
                valid = valid,
            )
        }
    }
}

enum class CrowdMonitorConnectionState {
    /** Not fetched yet this session. */
    Idle,

    /** WebSocket request in flight. */
    Connecting,

    /** Snapshot received successfully. */
    Connected,

    /** Offline / timeout / parse error — app remains usable. */
    Unavailable,
}

data class CrowdMonitorSnapshot(
    val connection: CrowdMonitorConnectionState = CrowdMonitorConnectionState.Idle,
    /** Keyed by CrowdMonitor uid (e.g. SSD-4). */
    val byUid: Map<String, CrowdOccupancy> = emptyMap(),
    val fetchedAtEpochMs: Long = 0L,
    val errorMessage: String? = null,
) {
    fun forUid(uid: String?): CrowdOccupancy? =
        if (uid.isNullOrBlank()) null else byUid[uid]
}
