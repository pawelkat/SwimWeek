package com.swimweek.app.data

import com.swimweek.app.domain.Pool
import com.swimweek.app.domain.PoolNoticeEntry
import com.swimweek.app.domain.PoolNoticeLoadState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lazy online cache of `stzh-disturber` notices per pool.
 * Does not throw; failed loads mark [PoolNoticeLoadState.Unavailable].
 */
@Singleton
class PoolNoticeRepository @Inject constructor(
    private val client: PoolNoticeClient,
) {
    private val mutex = Mutex()
    private val _entries = MutableStateFlow<Map<String, PoolNoticeEntry>>(emptyMap())
    val entries: StateFlow<Map<String, PoolNoticeEntry>> = _entries.asStateFlow()

    fun entry(poolId: String): PoolNoticeEntry? = _entries.value[poolId]

    /**
     * Ensure notice is loaded for [pool] (needs [Pool.url]).
     * Skips network if a fresh Loaded/Unavailable entry exists (unless [force]).
     */
    suspend fun ensureLoaded(pool: Pool, force: Boolean = false): PoolNoticeEntry {
        val url = pool.url
        if (url.isNullOrBlank()) {
            val empty = PoolNoticeEntry(
                poolId = pool.id,
                state = PoolNoticeLoadState.Loaded,
                notices = emptyList(),
                fetchedAtEpochMs = System.currentTimeMillis(),
            )
            _entries.update { it + (pool.id to empty) }
            return empty
        }

        mutex.withLock {
            val existing = _entries.value[pool.id]
            if (!force && existing != null && existing.state != PoolNoticeLoadState.Idle) {
                val age = System.currentTimeMillis() - existing.fetchedAtEpochMs
                if (existing.state == PoolNoticeLoadState.Loading) return existing
                if (existing.fetchedAtEpochMs > 0 && age < CACHE_TTL_MS) return existing
            }
            _entries.update {
                it + (
                    pool.id to PoolNoticeEntry(
                        poolId = pool.id,
                        state = PoolNoticeLoadState.Loading,
                        notices = existing?.notices.orEmpty(),
                        fetchedAtEpochMs = existing?.fetchedAtEpochMs ?: 0L,
                    )
                    )
            }
        }

        val result = client.fetchNotices(url)
        val next = result.fold(
            onSuccess = { list ->
                PoolNoticeEntry(
                    poolId = pool.id,
                    state = PoolNoticeLoadState.Loaded,
                    notices = list,
                    fetchedAtEpochMs = System.currentTimeMillis(),
                )
            },
            onFailure = { e ->
                PoolNoticeEntry(
                    poolId = pool.id,
                    state = PoolNoticeLoadState.Unavailable,
                    notices = emptyList(),
                    fetchedAtEpochMs = System.currentTimeMillis(),
                    errorMessage = e.message,
                )
            },
        )
        _entries.update { it + (pool.id to next) }
        return next
    }

    /** Prefetch several pools in parallel (favorites on recommendations). */
    suspend fun ensureLoadedMany(pools: List<Pool>, force: Boolean = false) {
        coroutineScope {
            pools
                .filter { !it.url.isNullOrBlank() }
                .distinctBy { it.id }
                .map { pool ->
                    async { ensureLoaded(pool, force = force) }
                }
                .awaitAll()
        }
    }

    companion object {
        const val CACHE_TTL_MS = 15 * 60_000L // 15 minutes
    }
}
