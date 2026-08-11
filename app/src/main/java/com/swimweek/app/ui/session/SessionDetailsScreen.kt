package com.swimweek.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.swimweek.app.R
import com.swimweek.app.data.FavoritePoolsStore
import com.swimweek.app.data.SessionPlaceStore
import com.swimweek.app.data.ZurichPoolsRepository
import com.swimweek.app.domain.OpeningHourSlot
import com.swimweek.app.domain.Pool
import com.swimweek.app.domain.SessionPlace
import com.swimweek.app.domain.SwimSession
import com.swimweek.app.health.SwimDistanceRepository
import com.swimweek.app.ui.theme.AmoledBlack
import com.swimweek.app.util.LengthFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionDetailsUiState(
    val session: SwimSession? = null,
    val place: SessionPlace? = null,
    val pools: List<Pool> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val selectedPool: Pool? = null,
    val notFound: Boolean = false,
)

@HiltViewModel
class SessionDetailsViewModel @Inject constructor(
    private val repository: SwimDistanceRepository,
    private val placeStore: SessionPlaceStore,
    private val poolsRepository: ZurichPoolsRepository,
    private val favoritePoolsStore: FavoritePoolsStore,
) : ViewModel() {

    private val sessionIdFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SessionDetailsUiState> = combine(
        sessionIdFlow,
        repository.summaryFlow,
        placeStore.placesFlow,
        favoritePoolsStore.favoriteIdsFlow,
    ) { sessionId, summary, places, favoriteIds ->
        if (sessionId == null) {
            return@combine SessionDetailsUiState(notFound = true)
        }
        val session = summary?.sessions?.firstOrNull { it.id == sessionId }
        val place = places[sessionId]
        val pools = poolsRepository.selectablePools()
            .sortedWith(
                compareByDescending<Pool> { it.id in favoriteIds }
                    .thenBy { it.name },
            )
        val selected = place?.poolId?.let { id -> pools.firstOrNull { it.id == id } }
        SessionDetailsUiState(
            session = session,
            place = place,
            pools = pools,
            favoriteIds = favoriteIds,
            selectedPool = selected,
            notFound = session == null,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SessionDetailsUiState(),
    )

    fun setSessionId(sessionId: String) {
        sessionIdFlow.value = sessionId
    }

    fun selectPool(pool: Pool) {
        val sessionId = sessionIdFlow.value ?: return
        viewModelScope.launch {
            placeStore.setPlace(sessionId, pool.id, pool.name)
        }
    }

    fun clearPool() {
        val sessionId = sessionIdFlow.value ?: return
        viewModelScope.launch {
            placeStore.clearPlace(sessionId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailsScreen(
    sessionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionDetailsViewModel = hiltViewModel(),
) {
    LaunchedEffect(sessionId) {
        viewModel.setSessionId(sessionId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

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

        // Pool name as title — dropdown of Zurich pools
        Text(
            text = stringResource(R.string.session_pool_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = state.selectedPool?.name
                    ?: state.place?.poolName
                    ?: stringResource(R.string.session_pool_unset),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.session_pool_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineSmall,
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.session_pool_unset),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {
                        viewModel.clearPool()
                        expanded = false
                    },
                )
                state.pools.forEach { pool ->
                    val isFavorite = pool.id in state.favoriteIds
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = if (isFavorite) {
                                        "★ ${pool.name}"
                                    } else {
                                        pool.name
                                    },
                                )
                                Text(
                                    text = if (isFavorite) {
                                        stringResource(R.string.session_pool_favorite_kind, pool.kind)
                                    } else {
                                        pool.kind.replaceFirstChar { it.uppercase() }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            viewModel.selectPool(pool)
                            expanded = false
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val session = state.session
        if (session == null) {
            Text(
                text = stringResource(R.string.session_not_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }

        Text(
            text = stringResource(R.string.session_details_heading),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))

        val zone = ZoneId.systemDefault()
        val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
        val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
        val startZ = session.start.atZone(zone)
        val endZ = session.end.atZone(zone)

        DetailLine(
            label = stringResource(R.string.session_when),
            value = "${dateFmt.format(startZ)} · ${timeFmt.format(startZ)} – ${timeFmt.format(endZ)}",
        )
        DetailLine(
            label = stringResource(R.string.session_distance),
            value = LengthFormat.format(
                session.distanceMeters,
                com.swimweek.app.domain.DistanceUnit.METERS,
                Locale.getDefault(),
            ),
        )
        DetailLine(
            label = stringResource(R.string.session_type),
            value = session.exerciseType.name,
        )
        DetailLine(
            label = stringResource(R.string.session_source),
            value = session.dataOriginPackage.ifBlank { "—" },
        )
        if (session.partialDistance || session.overlapSuppressed) {
            DetailLine(
                label = stringResource(R.string.session_flags),
                value = buildString {
                    if (session.partialDistance) append(stringResource(R.string.session_flag_partial))
                    if (session.partialDistance && session.overlapSuppressed) append(" · ")
                    if (session.overlapSuppressed) append(stringResource(R.string.session_flag_overlap))
                },
            )
        }

        val pool = state.selectedPool
        if (pool != null) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.session_hours_heading),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            pool.description?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            pool.hoursNote?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (pool.openingHours.isEmpty()) {
                Text(
                    text = stringResource(R.string.session_hours_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                pool.openingHours.forEach { slot ->
                    HoursRow(slot)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.session_hours_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.session_pick_pool_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(0.6f),
        )
    }
}

@Composable
private fun HoursRow(slot: OpeningHourSlot) {
    val left = slot.label
        ?: slot.period
        ?: slot.days?.joinToString(", ") { it.replaceFirstChar(Char::uppercase) }
        ?: "—"
    val right = buildString {
        when {
            slot.open != null && slot.close != null -> append("${slot.open}–${slot.close}")
            slot.timeText != null -> append(slot.timeText)
            else -> append("—")
        }
        slot.condition?.let { cond ->
            append(" · ")
            append(
                when (cond) {
                    "any_weather" -> stringResource(R.string.session_hours_any_weather)
                    "fair_weather" -> stringResource(R.string.session_hours_fair_weather)
                    else -> cond
                },
            )
        }
        slot.audience?.let { append(" · ").append(it) }
        slot.note?.let { append(" · ").append(it) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = left,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Text(
            text = right,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(0.55f),
        )
    }
}
