package com.swimweek.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swimweek.app.R
import com.swimweek.app.domain.DistanceUnit
import com.swimweek.app.domain.SwimSession
import com.swimweek.app.domain.WeekStatPoint
import com.swimweek.app.domain.resolvedPlaceName
import com.swimweek.app.ui.theme.AmoledAccent
import com.swimweek.app.ui.theme.AmoledTextMuted
import com.swimweek.app.ui.theme.AmoledTextSecondary
import com.swimweek.app.util.LengthFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max

/**
 * Multi-week bar chart: result (filled) vs target (secondary bar).
 * Each week column is clickable and opens a visit history for that week.
 */
@Composable
fun WeeklyStatsBarChart(
    points: List<WeekStatPoint>,
    unit: DistanceUnit,
    sessionPlaces: Map<String, String>,
    onOpenSession: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) {
        Text(
            text = stringResource(R.string.home_stats_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    var selectedWeek by remember { mutableStateOf<WeekStatPoint?>(null) }

    val maxMeters = points.maxOf { max(it.resultMeters, it.targetMeters) }
        .coerceAtLeast(1.0)
    val resultColor = AmoledAccent
    val targetColor = AmoledTextMuted

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_stats_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_stats_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LegendSwatch(color = resultColor)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.home_stats_legend_result),
                style = MaterialTheme.typography.labelSmall,
                color = AmoledTextSecondary,
            )
            Spacer(modifier = Modifier.width(16.dp))
            LegendSwatch(color = targetColor, outlined = true)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.home_stats_legend_target),
                style = MaterialTheme.typography.labelSmall,
                color = AmoledTextSecondary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            points.forEach { point ->
                WeekBarColumn(
                    point = point,
                    maxMeters = maxMeters,
                    resultColor = resultColor,
                    targetColor = targetColor,
                    onClick = { selectedWeek = point },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            points.forEach { point ->
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (point.isCurrentWeek) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        val scaleTop = LengthFormat.format(maxMeters, unit)
        Text(
            text = stringResource(R.string.home_stats_scale, scaleTop),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )
    }

    selectedWeek?.let { week ->
        WeekVisitsDialog(
            point = week,
            unit = unit,
            sessionPlaces = sessionPlaces,
            onDismiss = { selectedWeek = null },
            onOpenSession = { id ->
                selectedWeek = null
                onOpenSession(id)
            },
        )
    }
}

@Composable
private fun WeekBarColumn(
    point: WeekStatPoint,
    maxMeters: Double,
    resultColor: Color,
    targetColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
    ) {
        val barPairWidth = size.width * 0.85f
        val barWidth = barPairWidth / 2.2f
        val gap = barWidth * 0.2f
        val chartBottom = size.height
        val maxH = size.height * 0.92f
        val centerX = size.width / 2f
        val resultH = (point.resultMeters / maxMeters * maxH).toFloat()
        val targetH = if (point.hasTarget) {
            (point.targetMeters / maxMeters * maxH).toFloat()
        } else {
            0f
        }
        val pairLeft = centerX - barPairWidth / 2f

        if (point.hasTarget && targetH > 0f) {
            drawRoundRect(
                color = targetColor,
                topLeft = Offset(pairLeft, chartBottom - targetH),
                size = Size(barWidth, targetH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
        }
        if (resultH > 0f) {
            val resultLeft = if (point.hasTarget) {
                pairLeft + barWidth + gap
            } else {
                centerX - barWidth / 2f
            }
            drawRoundRect(
                color = resultColor,
                topLeft = Offset(resultLeft, chartBottom - resultH),
                size = Size(barWidth, resultH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
        } else if (!point.hasTarget) {
            drawRoundRect(
                color = targetColor.copy(alpha = 0.4f),
                topLeft = Offset(centerX - barWidth / 2f, chartBottom - 3.dp.toPx()),
                size = Size(barWidth, 3.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun WeekVisitsDialog(
    point: WeekStatPoint,
    unit: DistanceUnit,
    sessionPlaces: Map<String, String>,
    onDismiss: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
    val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
    val visits = remember(point) {
        point.sessions.sortedBy { it.start }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.home_stats_week_visits_title, point.label))
        },
        text = {
            if (visits.isEmpty()) {
                Text(stringResource(R.string.home_stats_week_visits_empty))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    visits.forEach { session ->
                        VisitRow(
                            session = session,
                            poolName = session.resolvedPlaceName(sessionPlaces[session.id]),
                            unit = unit,
                            dateFmt = dateFmt,
                            timeFmt = timeFmt,
                            zone = zone,
                            onClick = { onOpenSession(session.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.home_stats_week_visits_close))
            }
        },
    )
}

@Composable
private fun VisitRow(
    session: SwimSession,
    poolName: String?,
    unit: DistanceUnit,
    dateFmt: DateTimeFormatter,
    timeFmt: DateTimeFormatter,
    zone: ZoneId,
    onClick: () -> Unit,
) {
    val startZ = session.start.atZone(zone)
    val endZ = session.end.atZone(zone)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = poolName ?: stringResource(R.string.session_pool_unset),
            style = MaterialTheme.typography.titleMedium,
            color = if (poolName != null) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = buildString {
                append(dateFmt.format(startZ))
                append(" · ")
                append(timeFmt.format(startZ))
                append(" – ")
                append(timeFmt.format(endZ))
                append(" · ")
                append(LengthFormat.format(session.distanceMeters, unit))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendSwatch(
    color: Color,
    outlined: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(width = 12.dp, height = 12.dp)
            .then(
                if (outlined) {
                    Modifier.background(color.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
                } else {
                    Modifier.background(color, RoundedCornerShape(2.dp))
                },
            ),
    )
}
