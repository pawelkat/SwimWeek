package com.swimweek.app.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.swimweek.app.R
import com.swimweek.app.data.SessionPlaceStore
import com.swimweek.app.domain.DistanceSource
import com.swimweek.app.domain.SourceStatus
import com.swimweek.app.domain.WeeklySwimSummary
import com.swimweek.app.health.SwimDistanceRepository
import com.swimweek.app.sync.SyncReason
import com.swimweek.app.ui.onboarding.SamsungBridgeChecklist
import com.swimweek.app.ui.theme.AmoledBlack
import com.swimweek.app.util.LengthFormat
import com.swimweek.app.util.RelativeTimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val summary: WeeklySwimSummary? = null,
    val sessionPlaces: Map<String, String> = emptyMap(),
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val repository: SwimDistanceRepository,
    sessionPlaceStore: SessionPlaceStore,
) : ViewModel() {

    val uiState: StateFlow<DiagnosticsUiState> = combine(
        repository.summaryFlow,
        sessionPlaceStore.placesFlow,
    ) { summary, places ->
        DiagnosticsUiState(
            summary = summary,
            sessionPlaces = places.mapValues { it.value.poolName },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DiagnosticsUiState(),
    )

    fun reportMissingData() {
        viewModelScope.launch {
            repository.setUserReportedMissingData(true)
        }
    }

    fun clearMissingReport() {
        viewModelScope.launch {
            repository.setUserReportedMissingData(false)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            repository.sync(SyncReason.MANUAL)
        }
    }
}

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    onOpenSession: (sessionId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = ui.summary

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.nav_back))
        }
        Text(
            text = stringResource(R.string.diagnostics_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))

        val s = summary
        if (s == null) {
            Text(
                text = stringResource(R.string.diagnostics_no_cache),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DiagLine(stringResource(R.string.diagnostics_status), s.sourceStatus.name)
            DiagLine(
                stringResource(R.string.diagnostics_total),
                LengthFormat.format(s.totalDistanceMeters, com.swimweek.app.domain.DistanceUnit.METERS, Locale.US),
            )
            DiagLine(stringResource(R.string.diagnostics_sessions), s.sessionCount.toString())
            DiagLine(
                stringResource(R.string.diagnostics_with_distance),
                s.sessionsWithDistanceCount.toString(),
            )
            DiagLine(
                stringResource(R.string.diagnostics_partial),
                s.partialDistanceSessionCount.toString(),
            )
            DiagLine(
                stringResource(R.string.diagnostics_synced),
                RelativeTimeFormat.formatUpdatedAgo(s.lastSyncedAt),
            )
            DiagLine(
                stringResource(R.string.diagnostics_week_key),
                s.week.identityKey(),
            )
            if (s.userReportedMissingData) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.diagnostics_user_reported),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.diagnostics_sessions_heading),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (s.sessions.isEmpty()) {
                Text(
                    text = stringResource(R.string.diagnostics_no_sessions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                s.sessions.forEach { session ->
                    val place = ui.sessionPlaces[session.id]
                    Text(
                        text = buildString {
                            append(place ?: stringResource(R.string.session_pool_unset))
                            append('\n')
                            append(session.exerciseType.name)
                            append(" · ")
                            append(String.format(Locale.US, "%.0f m", session.distanceMeters))
                            append(" · ")
                            append(session.distanceSource.shortLabel())
                            if (session.overlapSuppressed) append(" · overlap")
                            append('\n')
                            append(session.dataOriginPackage.ifBlank { "?" })
                            session.title?.let { append(" · ").append(it) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSession(session.id) }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { viewModel.refresh() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_refresh))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.reportMissingData() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.diagnostics_i_swam))
        }
        if (summary?.userReportedMissingData == true ||
            summary?.sourceStatus == SourceStatus.USER_REPORTED_MISSING_BRIDGE
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { viewModel.clearMissingReport() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.diagnostics_clear_report))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SamsungBridgeChecklist(showTitle = true)
    }
}

@Composable
private fun DiagLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

private fun DistanceSource.shortLabel(): String =
    when (this) {
        DistanceSource.AGGREGATE_ORIGIN_FILTERED -> "HC aggregate"
        DistanceSource.LAP_LENGTHS -> "laps"
        DistanceSource.NONE -> "no distance"
    }
