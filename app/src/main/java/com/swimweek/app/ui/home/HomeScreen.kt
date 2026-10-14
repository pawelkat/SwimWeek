package com.swimweek.app.ui.home

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swimweek.app.R
import com.swimweek.app.domain.SourceStatus
import com.swimweek.app.domain.TargetProgress
import com.swimweek.app.domain.WeeklySwimSummary
import com.swimweek.app.domain.isManualEntry
import com.swimweek.app.domain.resolvedPlaceName
import com.swimweek.app.ui.components.SwimIconWithProgress
import com.swimweek.app.ui.theme.AmoledBlack
import com.swimweek.app.util.GoogleWalletLauncher
import com.swimweek.app.util.LengthFormat
import com.swimweek.app.widget.SwimWeekWidgetReceiver
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onOpenOnboarding: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSession: (sessionId: String) -> Unit = {},
    onOpenPoolRecommendations: () -> Unit = {},
    onLogSwim: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val summary = state.summary

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_week_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))

        when {
            state.refreshing && summary == null -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            else -> {
                val meters = summary?.totalDistanceMeters ?: 0.0
                val targetM = state.preferences.weeklyTargetMeters
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SwimIconWithProgress(
                        distanceMeters = meters,
                        targetMeters = targetM,
                        iconSize = 102.dp, // 120dp * 0.85
                        strokeWidth = 5.dp,
                        iconTint = MaterialTheme.colorScheme.primary,
                        progressColor = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(28.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = LengthFormat.format(meters, state.distanceUnit),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (TargetProgress.hasTarget(targetM)) {
                            val pct = (TargetProgress.fraction(meters, targetM) * 100f)
                                .roundToInt()
                            Text(
                                text = stringResource(
                                    R.string.home_target_progress,
                                    LengthFormat.format(targetM, state.distanceUnit),
                                    pct,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.home_session_count,
                        summary?.sessionCount ?: 0,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if ((summary?.partialDistanceSessionCount ?: 0) > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.home_partial_sessions,
                            summary?.partialDistanceSessionCount ?: 0,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = statusLabel(summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (summary?.sourceStatus) {
                        SourceStatus.ERROR,
                        SourceStatus.PERMISSIONS_MISSING,
                        SourceStatus.USER_REPORTED_MISSING_BRIDGE,
                        -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if ((summary?.sessionCount ?: 0) == 0) {
                    emptyStateHint(summary?.sourceStatus)?.let { hintRes ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(hintRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }

        state.errorMessage?.let { err ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = err,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        OutlinedButton(
            onClick = onLogSwim,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_log_swim))
        }

        Spacer(modifier = Modifier.height(28.dp))
        WeeklyStatsBarChart(
            points = state.weekStats,
            unit = state.distanceUnit,
            sessionPlaces = state.sessionPlaces,
            onOpenSession = onOpenSession,
            modifier = Modifier.fillMaxWidth(),
        )

        val sessions = summary?.sessions.orEmpty()
        if (sessions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.home_sessions_heading),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_sessions_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            val zone = ZoneId.systemDefault()
            val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
            val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(Locale.getDefault())
            sessions.forEach { session ->
                val startZ = session.start.atZone(zone)
                val placeName = session.resolvedPlaceName(state.sessionPlaces[session.id])
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSession(session.id) }
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        text = placeName
                            ?: stringResource(R.string.session_pool_unset),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (placeName != null) {
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
                            append(" · ")
                            append(LengthFormat.format(session.distanceMeters, state.distanceUnit))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { viewModel.refresh() },
            enabled = !state.refreshing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.refreshing) {
                    stringResource(R.string.home_refreshing)
                } else {
                    stringResource(R.string.home_refresh)
                },
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenPoolRecommendations,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_pool_recommendations))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                val opened = GoogleWalletLauncher.open(context)
                if (!opened) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.wallet_open_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_open_wallet))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenDiagnostics,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_diagnostics))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_settings))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenOnboarding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_setup_guide))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                val manager = AppWidgetManager.getInstance(context)
                val provider = ComponentName(context, SwimWeekWidgetReceiver::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    manager.isRequestPinAppWidgetSupported
                ) {
                    manager.requestPinAppWidget(provider, null, null)
                    Toast.makeText(
                        context,
                        context.getString(R.string.home_widget_pinned),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.home_widget_pin_unsupported),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_add_widget))
        }
    }
}

@Composable
private fun statusLabel(summary: WeeklySwimSummary?): String {
    val loggedOnly = summary != null &&
        summary.sourceStatus == SourceStatus.NO_DATA &&
        summary.sessions.any { it.isManualEntry() && it.distanceMeters > 0.0 && !it.overlapSuppressed }
    if (loggedOnly) return stringResource(R.string.home_status_logged)
    return when (summary?.sourceStatus) {
        null -> stringResource(R.string.home_status_loading)
        SourceStatus.OK -> stringResource(R.string.home_status_ok)
        SourceStatus.NO_DATA -> stringResource(R.string.home_status_no_data)
        SourceStatus.PERMISSIONS_MISSING -> stringResource(R.string.home_status_permissions)
        SourceStatus.HEALTH_CONNECT_UNAVAILABLE -> stringResource(R.string.home_status_hc_unavailable)
        SourceStatus.USER_REPORTED_MISSING_BRIDGE -> stringResource(R.string.home_status_bridge)
        SourceStatus.ERROR -> stringResource(R.string.home_status_error)
    }
}

private fun emptyStateHint(status: SourceStatus?): Int? =
    when (status) {
        SourceStatus.NO_DATA -> R.string.home_hint_no_data
        SourceStatus.PERMISSIONS_MISSING -> R.string.home_hint_permissions
        SourceStatus.USER_REPORTED_MISSING_BRIDGE -> R.string.home_hint_bridge
        SourceStatus.HEALTH_CONNECT_UNAVAILABLE -> R.string.home_hint_hc
        else -> null
    }
