package com.swimweek.app.widget

import android.content.Context
import android.content.Intent
import android.util.TypedValue
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.fillMaxHeight
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
 * AMOLED home-screen widget: pure #000000.
 * Icon + progress ring sized to nearly fill the widget height (exact size mode).
 */
class SwimWeekWidget : GlanceAppWidget() {

    /** Exact size so LocalSize matches the real widget bounds on the launcher. */
    override val sizeMode: SizeMode = SizeMode.Exact

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
}

@Composable
private fun SwimWeekWidgetContent(
    summary: WeeklySwimSummary?,
    preferences: UserPreferences,
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val unit = preferences.distanceUnit
    val meters = summary?.totalDistanceMeters ?: 0.0
    val targetM = preferences.weeklyTargetMeters
    val distanceText = LengthFormat.formatCompact(meters, unit)
    val sessions = summary?.sessionCount ?: 0
    val status = summary?.sourceStatus
    val compact = size.height < 90.dp
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

    // --- Maximize ring+icon: fill almost entire widget height ---
    val outerPad = 0.dp
    val gap = 16.dp // keep separation from distance text
    val minTextCol = 48.dp
    val availH = size.height.coerceAtLeast(40.dp)
    val availWForIcon = (size.width - gap - minTextCol).coerceAtLeast(40.dp)
    // Prefer full height; only shrink if not enough width for a square.
    // Then scale to 85% so the ring sits with a bit of breathing room.
    val maxIconSide = when {
        availH.value <= availWForIcon.value -> availH
        else -> availWForIcon
    }
    val iconSide = (maxIconSide.value * 0.85f).dp.coerceAtLeast(36.dp)

    val distanceSp = when {
        iconSide.value >= 120f -> 28.sp
        iconSide.value >= 90f -> 24.sp
        iconSide.value >= 64f -> 20.sp
        else -> 16.sp
    }
    val secondarySp = if (compact) 10.sp else 12.sp

    val iconSidePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        iconSide.value,
        context.resources.displayMetrics,
    ).roundToInt().coerceAtLeast(96)

    val ringBitmap = ProgressRingBitmap.create(
        sizePx = iconSidePx,
        distanceMeters = meters,
        targetMeters = targetM,
        strokePx = (iconSidePx * 0.09f).coerceIn(6f, 18f),
    )
    // Thin pad so swimmer stays large inside ring
    val iconPad = if (ringBitmap != null) {
        (iconSide.value * 0.10f).dp.coerceIn(2.dp, 12.dp)
    } else {
        2.dp
    }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(AmoledColors.Black)
            .padding(horizontal = 4.dp, vertical = 0.dp)
            .clickable(actionStartActivity(openApp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(iconSide)
                .fillMaxHeight(),
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
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    color = AmoledColors.secondaryText(),
                    fontSize = secondarySp,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 1,
            )
            if (targetLine != null) {
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
        }
    }
}
