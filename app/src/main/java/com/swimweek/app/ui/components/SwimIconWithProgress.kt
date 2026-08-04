package com.swimweek.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swimweek.app.R
import com.swimweek.app.domain.TargetProgress
import com.swimweek.app.ui.theme.AmoledAccent
import com.swimweek.app.ui.theme.AmoledTextMuted

/**
 * Swim icon with optional circular progress ring (weekly target completion).
 * Ring is hidden when [targetMeters] ≤ 0.
 */
@Composable
fun SwimIconWithProgress(
    distanceMeters: Double,
    targetMeters: Double,
    modifier: Modifier = Modifier,
    iconSize: Dp = 72.dp,
    iconTint: Color = AmoledAccent,
    trackColor: Color = AmoledTextMuted.copy(alpha = 0.35f),
    progressColor: Color = AmoledAccent,
    strokeWidth: Dp = 4.dp,
) {
    val progress = TargetProgress.fraction(distanceMeters, targetMeters)
    val showRing = TargetProgress.hasTarget(targetMeters)
    val iconPadding = if (showRing) strokeWidth + 6.dp else 0.dp

    Box(
        modifier = modifier.size(iconSize),
        contentAlignment = Alignment.Center,
    ) {
        if (showRing) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = strokeWidth.toPx()
                val diameter = minOf(this.size.width, this.size.height) - stroke
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                val arcSize = Size(diameter, diameter)
                // Track
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                // Progress (clockwise from top)
                if (progress > 0f) {
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Image(
            painter = painterResource(R.drawable.ic_swim),
            contentDescription = stringResource(R.string.swim_icon_cd),
            modifier = Modifier
                .fillMaxSize()
                .padding(iconPadding),
            colorFilter = ColorFilter.tint(iconTint),
        )
    }
}
