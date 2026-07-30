package com.swimweek.app.health

import com.swimweek.app.data.ChangesTokenStore
import com.swimweek.app.data.PreferencesStore
import com.swimweek.app.data.SummaryCacheStore
import com.swimweek.app.domain.SourceStatus
import com.swimweek.app.domain.WeekRange
import com.swimweek.app.domain.WeeklySwimSummary
import com.swimweek.app.sync.WidgetUpdater
import com.swimweek.app.util.ClockProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Orchestrates weekly aggregation + cache; pushes Glance updates via [WidgetUpdater].
 */
@Singleton
class SwimDistanceRepository @Inject constructor(
    private val aggregator: SwimDistanceAggregator,
    private val summaryCache: SummaryCacheStore,
    private val changesTokenStore: ChangesTokenStore,
    private val preferencesStore: PreferencesStore,
    private val availability: HealthConnectAvailability,
    private val permissionChecker: HealthPermissionChecker,
    private val clock: ClockProvider,
    private val widgetUpdater: WidgetUpdater,
) {
    private val inMemory = MutableStateFlow<WeeklySwimSummary?>(null)

    /** Cache + latest in-memory refresh for UI. */
    val summaryFlow: Flow<WeeklySwimSummary?> =
        combine(summaryCache.summaryFlow, inMemory) { cached, mem -> mem ?: cached }

    /**
     * Refresh weekly summary from Health Connect.
     * Forces full re-aggregate when week identity changed vs cache.
     */
    suspend fun refreshWeeklySummary(forceFull: Boolean = false): WeeklySwimSummary {
        val prefs = preferencesStore.get()
        val week = WeekRange.current(
            zoneId = clock.zoneId(),
            weekStart = prefs.weekStart,
            clock = clock.clock(),
        )

        if (!availability.isAvailable()) {
            return cacheAndReturn(
                WeeklySwimSummary.empty(
                    week = week,
                    lastSyncedAt = clock.now(),
                    sourceStatus = SourceStatus.HEALTH_CONNECT_UNAVAILABLE,
                ),
            )
        }

        if (!permissionChecker.hasRequiredPermissions()) {
            return cacheAndReturn(
                WeeklySwimSummary.empty(
                    week = week,
                    lastSyncedAt = clock.now(),
                    sourceStatus = SourceStatus.PERMISSIONS_MISSING,
                ),
            )
        }

        val cached = summaryCache.summaryFlow.first()
        val weekChanged = cached != null && cached.week.identityKey() != week.identityKey()
        // forceFull reserved for PR 6 changes path; week change always re-aggregates
        @Suppress("UNUSED_PARAMETER")
        val shouldForce = forceFull || weekChanged

        return try {
            val previousUserFlag = cached
                ?.takeIf { it.week.identityKey() == week.identityKey() }
                ?.userReportedMissingData
                ?: false

            val summary = aggregator.aggregateWeek(week).let { s ->
                // Clear user-reported missing when we actually found sessions
                if (s.sessionCount > 0) {
                    s.copy(userReportedMissingData = false)
                } else {
                    s.copy(
                        userReportedMissingData = previousUserFlag,
                        sourceStatus = if (previousUserFlag) {
                            SourceStatus.USER_REPORTED_MISSING_BRIDGE
                        } else {
                            s.sourceStatus
                        },
                    )
                }
            }
            cacheAndReturn(summary)
        } catch (e: SecurityException) {
            cacheAndReturn(
                WeeklySwimSummary.empty(
                    week = week,
                    lastSyncedAt = clock.now(),
                    sourceStatus = SourceStatus.PERMISSIONS_MISSING,
                ),
            )
        } catch (e: Exception) {
            val fallback = cached?.copy(
                lastSyncedAt = clock.now(),
                sourceStatus = SourceStatus.ERROR,
            ) ?: WeeklySwimSummary.empty(
                week = week,
                lastSyncedAt = clock.now(),
                sourceStatus = SourceStatus.ERROR,
            )
            cacheAndReturn(fallback)
        }
    }

    /**
     * Clears summary + changes token + in-memory state (permission revoke path).
     */
    suspend fun clearLocalHealthState() {
        summaryCache.clear()
        changesTokenStore.clear()
        inMemory.value = null
        widgetUpdater.updateAll()
    }

    suspend fun setUserReportedMissingData(reported: Boolean) {
        val current = summaryFlow.first() ?: return
        val updated = current.copy(
            userReportedMissingData = reported,
            sourceStatus = when {
                reported && current.sessionCount == 0 -> SourceStatus.USER_REPORTED_MISSING_BRIDGE
                current.sessionCount == 0 -> SourceStatus.NO_DATA
                else -> current.sourceStatus
            },
        )
        cacheAndReturn(updated)
    }

    private suspend fun cacheAndReturn(summary: WeeklySwimSummary): WeeklySwimSummary {
        summaryCache.save(summary)
        inMemory.value = summary
        widgetUpdater.updateAll()
        return summary
    }
}
