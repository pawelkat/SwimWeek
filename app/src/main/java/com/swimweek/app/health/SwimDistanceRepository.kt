package com.swimweek.app.health

import com.swimweek.app.data.ChangesTokenStore
import com.swimweek.app.data.PreferencesStore
import com.swimweek.app.data.SummaryCacheStore
import com.swimweek.app.domain.SourceStatus
import com.swimweek.app.domain.WeekRange
import com.swimweek.app.domain.WeeklySwimSummary
import com.swimweek.app.sync.SyncReason
import com.swimweek.app.sync.WidgetUpdater
import com.swimweek.app.util.ClockProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Orchestrates weekly aggregation, Changes-token incremental sync, cache,
 * and Glance updates via [WidgetUpdater].
 */
@Singleton
class SwimDistanceRepository @Inject constructor(
    private val aggregator: SwimDistanceAggregator,
    private val summaryCache: SummaryCacheStore,
    private val changesTokenStore: ChangesTokenStore,
    private val changesSyncEngine: ChangesSyncEngine,
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
     * Entry used by UI and workers.
     * - MANUAL / APP_FOREGROUND / MIDNIGHT / TOKEN_EXPIRED → prefer full aggregate
     * - PERIODIC → Changes drain first; re-aggregate only if needed
     */
    suspend fun sync(reason: SyncReason): WeeklySwimSummary {
        val forceFull = when (reason) {
            SyncReason.MANUAL,
            SyncReason.APP_FOREGROUND,
            SyncReason.MIDNIGHT,
            SyncReason.PERMISSION_GRANTED,
            SyncReason.WEEK_IDENTITY_CHANGED,
            SyncReason.TOKEN_EXPIRED,
            SyncReason.WIDGET_PIN,
            -> true
            SyncReason.PERIODIC,
            SyncReason.CHANGES,
            -> false
        }
        return refreshWeeklySummary(forceFull = forceFull, reason = reason)
    }

    /**
     * Refresh weekly summary from Health Connect.
     * Forces full re-aggregate when week identity changed vs cache or [forceFull].
     */
    suspend fun refreshWeeklySummary(
        forceFull: Boolean = false,
        @Suppress("UNUSED_PARAMETER") reason: SyncReason = SyncReason.MANUAL,
    ): WeeklySwimSummary {
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
        val noToken = changesTokenStore.get().isNullOrBlank()
        var doFull = forceFull || weekChanged || noToken

        if (!doFull) {
            val drain = changesSyncEngine.drain()
            if (drain.tokenExpired) {
                doFull = true
            } else if (!drain.needsReaggregate) {
                // No relevant HC changes — keep cache, touch lastSynced optional
                val keep = cached ?: return fullAggregateAndToken(week, cached)
                // If week identity still matches, return cache (widget already correct)
                if (keep.week.identityKey() == week.identityKey()) {
                    return keep
                }
                doFull = true
            } else {
                doFull = true
            }
        }

        return if (doFull) {
            fullAggregateAndToken(week, cached)
        } else {
            cached ?: fullAggregateAndToken(week, null)
        }
    }

    private suspend fun fullAggregateAndToken(
        week: WeekRange,
        cached: WeeklySwimSummary?,
    ): WeeklySwimSummary {
        return try {
            val previousUserFlag = cached
                ?.takeIf { it.week.identityKey() == week.identityKey() }
                ?.userReportedMissingData
                ?: false

            val summary = aggregator.aggregateWeek(week).let { s ->
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
            val saved = cacheAndReturn(summary)
            // Fresh changes token after full aggregate
            changesSyncEngine.issueToken()
            saved
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
