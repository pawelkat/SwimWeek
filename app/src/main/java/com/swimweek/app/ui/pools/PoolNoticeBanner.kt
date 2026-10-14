package com.swimweek.app.ui.pools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swimweek.app.R
import com.swimweek.app.domain.PoolNoticeEntry
import com.swimweek.app.domain.PoolNoticeLoadState
import java.time.Instant

/**
 * Displays active online CMS notices (stzh-disturber), especially closures.
 */
@Composable
fun PoolNoticeBanner(
    entry: PoolNoticeEntry?,
    now: Instant = Instant.now(),
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (entry == null || entry.state == PoolNoticeLoadState.Idle) {
        return
    }
    when (entry.state) {
        PoolNoticeLoadState.Loading -> {
            Text(
                text = stringResource(R.string.notice_loading),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = modifier,
            )
        }
        PoolNoticeLoadState.Unavailable -> {
            // Quiet: notice is optional; no noisy error
            if (!compact) {
                Text(
                    text = stringResource(R.string.notice_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = modifier,
                )
            }
        }
        PoolNoticeLoadState.Loaded -> {
            val active = entry.activeNotices(now)
            // Prefer closure-like; if only promos, skip (don't spam Abo ads)
            val toShow = active.filter { it.isClosureLike }.ifEmpty {
                emptyList()
            }
            if (toShow.isEmpty()) return
            Column(modifier = modifier.fillMaxWidth()) {
                toShow.forEach { notice ->
                    Text(
                        text = notice.label,
                        style = if (compact) {
                            MaterialTheme.typography.labelMedium
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
        PoolNoticeLoadState.Idle -> Unit
    }
}
