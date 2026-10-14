package com.swimweek.app.domain

import java.time.Instant
import java.time.ZonedDateTime

/**
 * Online CMS notice from Stadt Zürich page (`stzh-show` + `stzh-disturber`).
 * Used for temporary closures (e.g. "Geschlossen bis 16. August (Revision)").
 */
data class PoolNotice(
    val label: String,
    val showFrom: Instant?,
    val hideFrom: Instant?,
    val isClosureLike: Boolean,
) {
    fun isActiveAt(now: Instant): Boolean {
        if (showFrom != null && now.isBefore(showFrom)) return false
        if (hideFrom != null && !now.isBefore(hideFrom)) return false
        // If no dates, still show the label when present (rare)
        return true
    }
}

enum class PoolNoticeLoadState {
    Idle,
    Loading,
    Loaded,
    Unavailable,
}

/**
 * Per-pool notice fetch result (lazy, online-only).
 */
data class PoolNoticeEntry(
    val poolId: String,
    val state: PoolNoticeLoadState = PoolNoticeLoadState.Idle,
    val notices: List<PoolNotice> = emptyList(),
    val fetchedAtEpochMs: Long = 0L,
    val errorMessage: String? = null,
) {
    fun activeNotices(now: Instant): List<PoolNotice> =
        notices.filter { it.isActiveAt(now) }

    fun activeClosureLabels(now: Instant): List<String> =
        activeNotices(now).filter { it.isClosureLike }.map { it.label }
}
