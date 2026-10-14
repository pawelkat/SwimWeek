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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.swimweek.app.data.CustomPoolStore
import com.swimweek.app.data.FavoritePoolsStore
import com.swimweek.app.data.ManualSwimStore
import com.swimweek.app.data.SessionPlaceStore
import com.swimweek.app.data.ZurichPoolsRepository
import com.swimweek.app.domain.DistanceUnit
import com.swimweek.app.domain.ManualSwim
import com.swimweek.app.domain.ManualSwimKind
import com.swimweek.app.domain.OpeningHourSlot
import com.swimweek.app.domain.Pool
import com.swimweek.app.domain.SessionPlace
import com.swimweek.app.domain.SwimSession
import com.swimweek.app.domain.withCustomPools
import com.swimweek.app.ui.pools.AddPoolForm
import com.swimweek.app.ui.pools.PoolPickerField
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
    val manual: ManualSwim? = null,
    val notFound: Boolean = false,
)

@HiltViewModel
class SessionDetailsViewModel @Inject constructor(
    private val repository: SwimDistanceRepository,
    private val placeStore: SessionPlaceStore,
    private val poolsRepository: ZurichPoolsRepository,
    private val favoritePoolsStore: FavoritePoolsStore,
    private val manualSwimStore: ManualSwimStore,
    private val customPoolStore: CustomPoolStore,
) : ViewModel() {

    private val sessionIdFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SessionDetailsUiState> = combine(
        sessionIdFlow,
        repository.summaryFlow,
        repository.sessionByIdFlow,
        placeStore.placesFlow,
        combine(
            favoritePoolsStore.favoriteIdsFlow,
            manualSwimStore.swimsFlow,
            customPoolStore.poolsFlow,
        ) { favoriteIds, manuals, customPools ->
            Triple(favoriteIds, manuals, customPools)
        },
    ) { sessionId, summary, historySessions, places, extras ->
        val (favoriteIds, manuals, customPools) = extras
        if (sessionId == null) {
            return@combine SessionDetailsUiState(notFound = true)
        }
        val session = summary?.sessions?.firstOrNull { it.id == sessionId }
            ?: historySessions[sessionId]
        val place = places[sessionId]
        val pools = poolsRepository.selectablePools().withCustomPools(customPools, favoriteIds)
        val manual = manuals[sessionId]
        val linkedPoolId = place?.poolId ?: manual?.poolId
        val selected = linkedPoolId?.let { id -> pools.firstOrNull { it.id == id } }
        SessionDetailsUiState(
            session = session,
            place = place,
            pools = pools,
            favoriteIds = favoriteIds,
            selectedPool = selected,
            manual = manual,
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
            val manual = manualSwimStore.getAll().firstOrNull { it.id == sessionId }
            if (manual?.kind == ManualSwimKind.POOL) {
                repository.updateManualPool(sessionId, pool.id, pool.name)
            } else {
                placeStore.setPlace(sessionId, pool.id, pool.name)
            }
        }
    }

    fun clearPool() {
        val sessionId = sessionIdFlow.value ?: return
        viewModelScope.launch {
            val manual = manualSwimStore.getAll().firstOrNull { it.id == sessionId }
            if (manual?.kind == ManualSwimKind.POOL) {
                repository.clearManualPool(sessionId)
            } else {
                placeStore.clearPlace(sessionId)
            }
        }
    }

    fun deleteManual(onDone: () -> Unit) {
        val sessionId = sessionIdFlow.value ?: return
        viewModelScope.launch {
            repository.deleteManualSwim(sessionId)
            onDone()
        }
    }
}

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
    var confirmDelete by remember { mutableStateOf(false) }
    var addingPool by remember { mutableStateOf(false) }
    val openWater = state.manual?.kind == ManualSwimKind.OPEN_WATER

    if (addingPool) {
        AddPoolForm(
            onBack = { addingPool = false },
            onSaved = { pool ->
                viewModel.selectPool(pool)
                addingPool = false
            },
            modifier = modifier,
        )
        return
    }

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

        if (openWater) {
            Text(
                text = state.manual?.name ?: stringResource(R.string.log_swim_kind_open_water),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.log_swim_kind_open_water),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (state.manual != null) {
                Text(
                    text = stringResource(R.string.log_swim_kind_pool),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Text(
                    text = stringResource(R.string.session_pool_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            PoolPickerField(
                pools = state.pools,
                favoriteIds = state.favoriteIds,
                selectedPool = state.selectedPool,
                fallbackName = state.place?.poolName,
                onSelect = viewModel::selectPool,
                onClear = viewModel::clearPool,
                onAdd = { addingPool = true },
            )
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
        val logged = state.manual != null
        val enteredMeters = state.manual?.distanceMeters
        val distanceMeters = if (logged && session.overlapSuppressed && enteredMeters != null) {
            enteredMeters
        } else {
            session.distanceMeters
        }
        val distanceText = LengthFormat.format(
            distanceMeters,
            DistanceUnit.METERS,
            Locale.getDefault(),
        )
        DetailLine(
            label = stringResource(R.string.session_distance),
            value = if (logged && session.overlapSuppressed) {
                stringResource(R.string.session_manual_distance_not_counted, distanceText)
            } else {
                distanceText
            },
        )
        if (!logged) {
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
        } else {
            if (state.manual?.watchMissed == true) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.log_swim_watch_missed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(
                    if (session.overlapSuppressed) {
                        R.string.session_manual_overlap
                    } else {
                        R.string.session_manual_estimate
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.session_manual_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.session_manual_delete_title)) },
                text = { Text(stringResource(R.string.session_manual_delete_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDelete = false
                            viewModel.deleteManual(onBack)
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.session_manual_delete_confirm),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) {
                        Text(stringResource(R.string.log_swim_cancel))
                    }
                },
            )
        }

        if (openWater) return@Column

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
                    text = stringResource(
                        if (pool.category == CustomPoolStore.CUSTOM_POOL_CATEGORY) {
                            R.string.add_pool_hint
                        } else {
                            R.string.session_hours_unavailable
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                pool.openingHours.forEach { slot ->
                    HoursRow(slot)
                }
            }
            if (pool.category != CustomPoolStore.CUSTOM_POOL_CATEGORY) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.session_hours_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
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
