package com.swimweek.app.widget

import android.content.Context
import android.content.Intent
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.swimweek.app.MainActivity
import com.swimweek.app.R
import com.swimweek.app.data.UserPreferences
import com.swimweek.app.di.swimWeekEntryPoint
import com.swimweek.app.domain.SourceStatus
import com.swimweek.app.domain.TargetProgress
import com.swimweek.app.domain.WeekRange
import com.swimweek.app.domain.WeeklySwimSummary
import com.swimweek.app.util.LengthFormat
import com.swimweek.app.util.RelativeTimeFormat
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * AMOLED home-screen widget: pure #000000, sparse fields, no system polling.
 * Swim icon with optional weekly-target progress ring.
 */
class SwimWeekWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            SMALL,
            MEDIUM,
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = context.swimWeekEntryPoint()
        val prefs = entry.preferencesStore().get()
        val cached = entry.summaryCacheStore().getOnce()
        val zone = ZoneId.systemDefault()
        val currentWeek = WeekRange.current(
            zoneId = zone,
            weekStart = prefs.weekStart,
        )
        val summary = when {
            cached == null -> null
            cached.week.identityKey() != currentWeek.identityKey() -> {
                WeeklySwimSummary.empty(
                    week = currentWeek,
                    lastSyncedAt = Instant.now(),
                    sourceStatus = SourceStatus.NO_DATA,
                )
            }
            else -> cached
        }

        provideContent {
            SwimWeekWidgetContent(
                summary = summary,
                preferences = prefs,
            )
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 72.dp)
        val MEDIUM = DpSize(200.dp, 140.dp)
    }
}

@Composable
private fun SwimWeekWidgetContent(
    summary: WeeklySwimSummary?,
    preferences: UserPreferences,
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val compact = size.height < 80.dp
    val unit = preferences.distanceUnit
    val meters = summary?.totalDistanceMeters ?: 0.0
    val targetM = preferences.weeklyTargetMeters
    val distanceText = LengthFormat.formatCompact(meters, unit)
    val sessions = summary?.sessionCount ?: 0
    val status = summary?.sourceStatus
    val subtitle = when (status) {
        SourceStatus.PERMISSIONS_MISSING -> "grant permissions"
        SourceStatus.HEALTH_CONNECT_UNAVAILABLE -> "Health Connect needed"
        SourceStatus.ERROR -> "tap to open"
        SourceStatus.USER_REPORTED_MISSING_BRIDGE -> "check Samsung bridge"
        else -> if (compact) {
            "$sessions swims"
        } else {
            "this week · $sessions swims"
        }
    }
    val targetLine = if (TargetProgress.hasTarget(targetM)) {
        val pct = (TargetProgress.fraction(meters, targetM) * 100f).roundToInt()
        "${LengthFormat.formatCompact(targetM, unit)} · $pct%"
    } else {
        null
    }
    val updated = summary?.lastSyncedAt?.let {
        RelativeTimeFormat.formatUpdatedAgo(it)
    }.orEmpty()
    val openApp = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    // Maximize icon+ring: nearly full height, as much width as possible while
    // leaving a slim column for distance text on the right.
    val outerPad = 2.dp
    val gap = 4.dp
    val minTextCol = if (compact) 52.dp else 64.dp
    val availH = (size.height - outerPad * 2).coerceAtLeast(24.dp)
    val availWForIcon = (size.width - outerPad * 2 - gap - minTextCol).coerceAtLeast(24.dp)
    // Square: largest that fits in remaining height AND width
    val iconSide = if (availH.value <= availWForIcon.value) availH else availWForIcon
    val distanceSp = when {
        compact && iconSide.value >= 40f -> 16.sp
        compact -> 14.sp
        iconSide.value >= 90f -> 26.sp
        else -> 22.sp
    }
    val secondarySp = if (compact) 10.sp else 11.sp
    val iconSidePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        iconSide.value,
        context.resources.displayMetrics,
    ).roundToInt().coerceAtLeast(64)
    val ringBitmap = ProgressRingBitmap.create(
        sizePx = iconSidePx,
        distanceMeters = meters,
        targetMeters = targetM,
        strokePx = (iconSidePx * 0.1f).coerceIn(4f, 14f),
    )
    // Keep swimmer large inside the ring (thin inset)
    val iconPad = if (ringBitmap != null) (iconSide.value * 0.12f).dp.coerceAtLeast(3.dp) else 0.dp

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(AmoledColors.Black)
            .padding(outerPad)
            .clickable(actionStartActivity(openApp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier.size(iconSide),
            contentAlignment = Alignment.Center,
        ) {
            if (ringBitmap != null) {
                Image(
                    provider = ImageProvider(ringBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Image(
                provider = ImageProvider(R.drawable.ic_swim),
                contentDescription = "Swimmer",
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(iconPad),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(AmoledColors.primaryText()),
            )
        }
        Spacer(modifier = GlanceModifier.width(gap))
        Column(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = distanceText,
                style = TextStyle(
                    color = AmoledColors.primaryText(),
                    fontSize = distanceSp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            if (!compact) {
                Spacer(modifier = GlanceModifier.height(2.dp))
            }
            Text(
                text = subtitle,
                style = TextStyle(
                    color = AmoledColors.secondaryText(),
                    fontSize = secondarySp,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 1,
            )
            if (!compact && targetLine != null) {
                Spacer(modifier = GlanceModifier.height(1.dp))
                Text(
                    text = targetLine,
                    style = TextStyle(
                        color = AmoledColors.secondaryText(),
                        fontSize = secondarySp,
                        fontWeight = FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
            if (!compact && updated.isNotEmpty() && status != SourceStatus.PERMISSIONS_MISSING) {
                Spacer(modifier = GlanceModifier.height(1.dp))
                Text(
                    text = updated,
                    style = TextStyle(
                        color = AmoledColors.tertiaryText(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
            if (!compact && (summary?.partialDistanceSessionCount ?: 0) > 0) {
                Spacer(modifier = GlanceModifier.height(1.dp))
                Text(
                    text = "partial distance",
                    style = TextStyle(
                        color = AmoledColors.secondaryText(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}
