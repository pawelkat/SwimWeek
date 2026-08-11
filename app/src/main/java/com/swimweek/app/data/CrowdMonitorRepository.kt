package com.swimweek.app.data

import com.swimweek.app.domain.CrowdMonitorConnectionState
import com.swimweek.app.domain.CrowdMonitorSnapshot
import com.swimweek.app.domain.CrowdOccupancy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Caches CrowdMonitor occupancy. Failures set [CrowdMonitorConnectionState.Unavailable]
 * without throwing — UI shows a small no-connection icon and stays usable offline.
 */
@Singleton
class CrowdMonitorRepository @Inject constructor(
    private val client: CrowdMonitorClient,
) {
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(CrowdMonitorSnapshot())
    val snapshot: StateFlow<CrowdMonitorSnapshot> = _snapshot.asStateFlow()

    /**
     * Refresh occupancy. Safe to call repeatedly; concurrent callers share one in-flight fetch.
     * @param force if false, skip when a fresh Connected snapshot exists within [minAgeMs]
     */
    suspend fun refresh(
        force: Boolean = false,
        minAgeMs: Long = CACHE_TTL_MS,
    ): CrowdMonitorSnapshot {
        mutex.withLock {
            val current = _snapshot.value
            if (!force &&
                current.connection == CrowdMonitorConnectionState.Connected &&
                current.fetchedAtEpochMs > 0 &&
                System.currentTimeMillis() - current.fetchedAtEpochMs < minAgeMs
            ) {
                return current
            }
            _snapshot.value = current.copy(
                connection = CrowdMonitorConnectionState.Connecting,
                errorMessage = null,
            )
        }

        val result = client.fetchAll()
        val next = result.fold(
            onSuccess = { list ->
                CrowdMonitorSnapshot(
                    connection = CrowdMonitorConnectionState.Connected,
                    byUid = list.associateBy { it.uid },
                    fetchedAtEpochMs = System.currentTimeMillis(),
                    errorMessage = null,
                )
            },
            onFailure = { e ->
                // Keep last good byUid if any, but mark unavailable
                val prev = _snapshot.value
                CrowdMonitorSnapshot(
                    connection = CrowdMonitorConnectionState.Unavailable,
                    byUid = prev.byUid,
                    fetchedAtEpochMs = prev.fetchedAtEpochMs,
                    errorMessage = e.message ?: e::class.java.simpleName,
                )
            },
        )
        _snapshot.value = next
        return next
    }

    fun occupancyForUid(uid: String?): CrowdOccupancy? = _snapshot.value.forUid(uid)

    companion object {
        const val CACHE_TTL_MS = 60_000L
    }
}
