package com.swimweek.app.ui.pools

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swimweek.app.R
import com.swimweek.app.domain.CrowdMonitorConnectionState
import com.swimweek.app.domain.CrowdOccupancy

/**
 * Stadt Zürich–style Auslastung: up to 4 person icons (filled + outline),
 * or a small no-connection glyph when CrowdMonitor is unreachable.
 */
@Composable
fun OccupancyIconsRow(
    connection: CrowdMonitorConnectionState,
    occupancy: CrowdOccupancy?,
    modifier: Modifier = Modifier,
    showGuestCount: Boolean = true,
    compact: Boolean = false,
) {
    val iconH = if (compact) 20.dp else 28.dp
    val iconW = if (compact) 9.dp else 12.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        when (connection) {
            CrowdMonitorConnectionState.Idle,
            CrowdMonitorConnectionState.Connecting,
            -> {
                Text(
                    text = stringResource(R.string.crowd_loading),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            CrowdMonitorConnectionState.Unavailable -> {
                Image(
                    painter = painterResource(R.drawable.ic_no_connection),
                    contentDescription = stringResource(R.string.crowd_no_connection_cd),
                    modifier = Modifier.size(if (compact) 16.dp else 20.dp),
                )
                if (!compact) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.crowd_no_connection),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            CrowdMonitorConnectionState.Connected -> {
                if (occupancy == null || !occupancy.valid || occupancy.level == null) {
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val filled = occupancy.level.coerceIn(1, 4)
                    repeat(4) { index ->
                        val res = if (index < filled) {
                            R.drawable.ic_maennlein_filled
                        } else {
                            R.drawable.ic_maennlein_outline
                        }
                        Image(
                            painter = painterResource(res),
                            contentDescription = null,
                            modifier = Modifier
                                .width(iconW)
                                .height(iconH)
                                .padding(end = 2.dp),
                        )
                    }
                    if (showGuestCount) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                R.string.crowd_guests,
                                occupancy.guests,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
