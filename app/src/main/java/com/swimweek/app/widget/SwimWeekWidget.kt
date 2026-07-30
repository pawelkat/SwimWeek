package com.swimweek.app.widget

import android.content.Context
import android.content.Intent
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
import com.swimweek.app.domain.WeekRange
import com.swimweek.app.domain.WeeklySwimSummary
import com.swimweek.app.util.LengthFormat
import com.swimweek.app.util.RelativeTimeFormat
import java.time.Instant
import java.time.ZoneId

/**
 * AMOLED home-screen widget: pure #000000, sparse fields, no system polling.
 * Reads cache via [com.swimweek.app.di.SwimWeekEntryPoint] (not Hilt inject).
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
                // Week rolled over; show empty until next app refresh / PR 6 worker
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
        val SMALL = DpSize(110.dp, 48.dp)
        val MEDIUM = DpSize(180.dp, 110.dp)
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
    val updated = summary?.lastSyncedAt?.let {
        RelativeTimeFormat.formatUpdatedAgo(it)
    }.orEmpty()
    val openApp = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    // Icon uses full widget height (minus outer padding); square box for the glyph.
    val outerPad = 8.dp
    val iconSide = (size.height - outerPad * 2).coerceAtLeast(28.dp)
    val distanceSp = if (compact) 18.sp else 28.sp

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(AmoledColors.Black)
            .padding(outerPad)
            .clickable(actionStartActivity(openApp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_swim),
            contentDescription = "Swimmer",
            modifier = GlanceModifier
                .size(iconSide)
                .fillMaxHeight(),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(AmoledColors.primaryText()),
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
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
                Spacer(modifier = GlanceModifier.height(4.dp))
            }
            Text(
                text = subtitle,
                style = TextStyle(
                    color = AmoledColors.secondaryText(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 1,
            )
            if (!compact && updated.isNotEmpty() && status != SourceStatus.PERMISSIONS_MISSING) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = updated,
                    style = TextStyle(
                        color = AmoledColors.tertiaryText(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
            if (!compact && (summary?.partialDistanceSessionCount ?: 0) > 0) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = "partial distance",
                    style = TextStyle(
                        color = AmoledColors.secondaryText(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}
