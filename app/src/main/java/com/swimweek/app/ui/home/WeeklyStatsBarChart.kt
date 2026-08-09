package com.swimweek.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swimweek.app.R
import com.swimweek.app.domain.DistanceUnit
import com.swimweek.app.domain.WeekStatPoint
import com.swimweek.app.ui.theme.AmoledAccent
import com.swimweek.app.ui.theme.AmoledTextMuted
import com.swimweek.app.ui.theme.AmoledTextSecondary
import com.swimweek.app.util.LengthFormat
import kotlin.math.max

/**
 * Basic multi-week bar chart: result (filled) vs target (outline / secondary bar).
 * AMOLED-safe colors; no external chart library.
 */
@Composable
fun WeeklyStatsBarChart(
    points: List<WeekStatPoint>,
    unit: DistanceUnit,
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

        // Legend
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

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            val n = points.size
            val groupWidth = size.width / n
            val barPairWidth = groupWidth * 0.55f
            val barWidth = barPairWidth / 2.2f
            val gap = barWidth * 0.2f
            val chartBottom = size.height
            val maxH = size.height * 0.92f

            points.forEachIndexed { index, point ->
                val centerX = groupWidth * index + groupWidth / 2f
                val resultH = (point.resultMeters / maxMeters * maxH).toFloat()
                val targetH = if (point.hasTarget) {
                    (point.targetMeters / maxMeters * maxH).toFloat()
                } else {
                    0f
                }
                val pairLeft = centerX - barPairWidth / 2f

                // Target bar (behind / left)
                if (point.hasTarget && targetH > 0f) {
                    drawRoundRect(
                        color = targetColor,
                        topLeft = Offset(pairLeft, chartBottom - targetH),
                        size = Size(barWidth, targetH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                }
                // Result bar (right of target)
                if (resultH > 0f) {
                    val resultLeft = if (point.hasTarget) pairLeft + barWidth + gap else centerX - barWidth / 2f
                    drawRoundRect(
                        color = resultColor,
                        topLeft = Offset(resultLeft, chartBottom - resultH),
                        size = Size(barWidth, resultH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                } else if (!point.hasTarget) {
                    // tiny tick so empty weeks are visible
                    drawRoundRect(
                        color = targetColor.copy(alpha = 0.4f),
                        topLeft = Offset(centerX - barWidth / 2f, chartBottom - 3.dp.toPx()),
                        size = Size(barWidth, 3.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    )
                }
            }
        }

        // X-axis labels
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
