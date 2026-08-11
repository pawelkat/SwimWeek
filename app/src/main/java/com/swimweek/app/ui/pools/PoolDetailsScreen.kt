package com.swimweek.app.ui.pools

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.swimweek.app.R
import com.swimweek.app.data.FavoritePoolsStore
import com.swimweek.app.data.ZurichPoolsRepository
import com.swimweek.app.domain.EveningPoolSelector
import com.swimweek.app.domain.OpenPoolRecommender
import com.swimweek.app.domain.OpeningHourSlot
import com.swimweek.app.domain.Pool
import com.swimweek.app.ui.theme.AmoledBlack
import com.swimweek.app.util.ClockProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PoolDetailsUiState(
    val pool: Pool? = null,
    val isFavorite: Boolean = false,
    val notFound: Boolean = false,
    val openStatus: String? = null,
    val minutesUntilClose: Long? = null,
)

@HiltViewModel
class PoolDetailsViewModel @Inject constructor(
    private val poolsRepository: ZurichPoolsRepository,
    private val favoritePoolsStore: FavoritePoolsStore,
    private val clockProvider: ClockProvider,
) : ViewModel() {

    private val poolIdFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PoolDetailsUiState> = combine(
        poolIdFlow,
        favoritePoolsStore.favoriteIdsFlow,
    ) { poolId, favorites ->
        if (poolId == null) return@combine PoolDetailsUiState(notFound = true)
        val pool = poolsRepository.findPool(poolId)
            ?: return@combine PoolDetailsUiState(notFound = true)
        val zone = clockProvider.zoneId()
        val nowZ = ZonedDateTime.ofInstant(clockProvider.now(), zone)
        val recs = OpenPoolRecommender.recommend(
            pools = listOf(pool),
            date = nowZ.toLocalDate(),
            now = nowZ.toLocalTime(),
            favoriteIds = favorites,
            seasonYear = poolsRepository.getCatalog().seasonYear ?: nowZ.year,
            includeClosingSoon = true,
        )
        val open = recs.firstOrNull()
        val openStatus = when {
            open == null -> null
            open.isRecommended -> "recommended"
            open.isClosingSoon -> "closing_soon"
            else -> null
        }
        PoolDetailsUiState(
            pool = pool,
            isFavorite = poolId in favorites,
            notFound = false,
            openStatus = openStatus,
            minutesUntilClose = open?.minutesUntilClose,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PoolDetailsUiState(),
    )

    fun setPoolId(poolId: String) {
        poolIdFlow.value = poolId
    }

    fun toggleFavorite() {
        val id = poolIdFlow.value ?: return
        viewModelScope.launch {
            favoritePoolsStore.toggleFavorite(id)
        }
    }
}

@Composable
fun PoolDetailsScreen(
    poolId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PoolDetailsViewModel = hiltViewModel(),
) {
    LaunchedEffect(poolId) {
        viewModel.setPoolId(poolId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val timeFmt = DateTimeFormatter.ofPattern("H:mm")

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

        val pool = state.pool
        if (pool == null) {
            Text(
                text = stringResource(R.string.pool_details_not_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = pool.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = state.isFavorite,
                onClick = { viewModel.toggleFavorite() },
                label = {
                    Text(
                        if (state.isFavorite) {
                            stringResource(R.string.recommend_favorite_on)
                        } else {
                            stringResource(R.string.recommend_favorite_off)
                        },
                    )
                },
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = pool.kind.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        pool.description?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (state.openStatus) {
            "recommended" -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        R.string.pool_details_open_now,
                        formatMinutes(state.minutesUntilClose ?: 0),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            "closing_soon" -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        R.string.pool_details_closing_soon,
                        formatMinutes(state.minutesUntilClose ?: 0),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.pool_details_closed_now),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        pool.hoursNote?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        pool.externalUrl?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pool_details_external, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.session_hours_heading),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (pool.openingHours.isEmpty()) {
            Text(
                text = stringResource(R.string.session_hours_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Prefer today's windows first for quick scan
            val zone = java.time.ZoneId.systemDefault()
            val today = java.time.LocalDate.now(zone)
            val todayWindows = EveningPoolSelector.windowsForDate(listOf(pool), today)
            if (todayWindows.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.pool_details_today_hours),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
                todayWindows.forEach { w ->
                    Text(
                        text = buildString {
                            append(w.open.format(timeFmt))
                            append("–")
                            append(w.close.format(timeFmt))
                            w.condition?.let { cond ->
                                append(" · ")
                                append(
                                    when (cond) {
                                        "any_weather" -> stringResource(R.string.session_hours_any_weather)
                                        "fair_weather" -> stringResource(R.string.session_hours_fair_weather)
                                        else -> cond
                                    },
                                )
                            }
                            w.audience?.let { append(" · ").append(it) }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.pool_details_full_schedule),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            pool.openingHours.forEach { slot ->
                PoolHoursRow(slot)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.session_hours_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun PoolHoursRow(slot: OpeningHourSlot) {
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

private fun formatMinutes(minutes: Long): String {
    if (minutes < 60) return "${minutes}m"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0L) "${h}h" else "${h}h ${m}m"
}
