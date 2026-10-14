package com.swimweek.app.domain

/**
 * Zurich Stadt pool catalogue entry (from bundled assets JSON).
 * [openingHours] is normalized for later session-planning logic.
 */
data class Pool(
    val id: String,
    val name: String,
    val kind: String,
    val category: String,
    val url: String? = null,
    val description: String? = null,
    val selectable: Boolean = true,
    val externalUrl: String? = null,
    val hoursNote: String? = null,
    /**
     * CrowdMonitor facility uid (e.g. SSD-4 for Hallenbad City).
     * Null when the pool has no live occupancy feed.
     */
    val crowdMonitorUid: String? = null,
    val openingHours: List<OpeningHourSlot> = emptyList(),
    val schedules: List<ScheduleTable> = emptyList(),
)

/**
 * Machine-friendly opening window.
 * [days] uses mon..sun (null for seasonal outdoor ranges keyed by [period]).
 * [condition] is null, "any_weather", or "fair_weather".
 */
data class OpeningHourSlot(
    val period: String? = null,
    val days: List<String>? = null,
    val open: String? = null,
    val close: String? = null,
    val condition: String? = null,
    val audience: String? = null,
    val label: String? = null,
    val timeText: String? = null,
    val note: String? = null,
)

/** Raw table scraped from the Stadt Zürich page (preserved for display / re-parse). */
data class ScheduleTable(
    val columns: List<String>,
    val entries: List<Map<String, String>>,
)

data class ZurichPoolsCatalog(
    val schemaVersion: Int,
    val retrievedAt: String?,
    val seasonYear: Int?,
    val sourceHallen: String?,
    val sourceSommer: String?,
    val pools: List<Pool>,
) {
    fun selectablePools(): List<Pool> = pools.filter { it.selectable }

    fun findById(id: String): Pool? = pools.firstOrNull { it.id == id }
}

/** Zurich catalogue plus pools the user added. Favorites sort first. */
fun List<Pool>.withCustomPools(custom: List<Pool>, favoriteIds: Set<String>): List<Pool> =
    (this + custom)
        .distinctBy { it.id }
        .sortedWith(
            compareByDescending<Pool> { it.id in favoriteIds }
                .thenBy { it.name.lowercase() },
        )

/**
 * Local mapping of a Health Connect exercise session id → chosen pool.
 * Pool name acts as the session place / title in the app (HC has no place field).
 */
data class SessionPlace(
    val sessionId: String,
    val poolId: String,
    val poolName: String,
    val updatedAtEpochMs: Long,
)
