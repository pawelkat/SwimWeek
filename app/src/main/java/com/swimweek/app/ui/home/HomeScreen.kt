package com.swimweek.app.ui.home

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.swimweek.app.ui.theme.AmoledBlack
import com.swimweek.app.util.LengthFormat
import com.swimweek.app.widget.SwimWeekWidgetReceiver

@Composable
fun HomeScreen(
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
            state.refreshing && state.summary == null -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            else -> {
                val summary = state.summary
                val meters = summary?.totalDistanceMeters ?: 0.0
                Text(
                    text = LengthFormat.format(meters, state.distanceUnit),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
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
                    text = statusLabel(summary?.sourceStatus),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                summary?.sessions?.firstOrNull()?.dataOriginPackage?.let { pkg ->
                    if (pkg.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.home_origin_sample, pkg),
                            style = MaterialTheme.typography.labelSmall,
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
        Button(
            onClick = onOpenPermissions,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_permissions))
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
private fun statusLabel(status: SourceStatus?): String {
    return when (status) {
        null -> stringResource(R.string.home_status_loading)
        SourceStatus.OK -> stringResource(R.string.home_status_ok)
        SourceStatus.NO_DATA -> stringResource(R.string.home_status_no_data)
        SourceStatus.PERMISSIONS_MISSING -> stringResource(R.string.home_status_permissions)
        SourceStatus.HEALTH_CONNECT_UNAVAILABLE -> stringResource(R.string.home_status_hc_unavailable)
        SourceStatus.USER_REPORTED_MISSING_BRIDGE -> stringResource(R.string.home_status_bridge)
        SourceStatus.ERROR -> stringResource(R.string.home_status_error)
    }
}
