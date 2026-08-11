package com.swimweek.app.ui.pools

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.swimweek.app.data.CrowdMonitorRepository
import com.swimweek.app.data.FavoritePoolsStore
import com.swimweek.app.data.PreferencesStore
import com.swimweek.app.data.UserPreferences
import com.swimweek.app.data.ZurichPoolsRepository
import com.swimweek.app.domain.CrowdMonitorSnapshot
import com.swimweek.app.domain.OpenPoolRecommender
import com.swimweek.app.domain.Pool
import com.swimweek.app.ui.theme.AmoledBlack
import com.swimweek.app.util.ClockProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PoolRecommendationsUiState(
    val asOfDate: LocalDate? = null,
    val asOfTime: LocalTime? = null,
    val topPick: OpenPoolRecommender.Recommendation? = null,
    val recommended: List<OpenPoolRecommender.Recommendation> = emptyList(),
    val closingSoon: List<OpenPoolRecommender.Recommendation> = emptyList(),
    val favoritesOffline: List<Pool> = emptyList(),
    val allPools: List<Pool> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val minMinutes: Long = OpenPoolRecommender.DEFAULT_MIN_MINUTES_UNTIL_CLOSE,
    val crowd: CrowdMonitorSnapshot = CrowdMonitorSnapshot(),
)

@HiltViewModel
class PoolRecommendationsViewModel @Inject constructor(
    private val poolsRepository: ZurichPoolsRepository,
    private val favoritePoolsStore: FavoritePoolsStore,
    private val preferencesStore: PreferencesStore,
    private val crowdMonitorRepository: CrowdMonitorRepository,
    private val clockProvider: ClockProvider,
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0)

    val uiState: StateFlow<PoolRecommendationsUiState> = combine(
        favoritePoolsStore.favoriteIdsFlow,
        preferencesStore.preferencesFlow,
        crowdMonitorRepository.snapshot,
        refreshTick,
    ) { favoriteIds, prefs, crowd, _ ->
        buildState(favoriteIds, prefs.recommendMinMinutesUntilClose.toLong(), crowd)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PoolRecommendationsUiState(
            minMinutes = UserPreferences.DEFAULT_RECOMMEND_MIN_MINUTES.toLong(),
        ),
    )

    init {
        viewModelScope.launch {
            // Never throws into UI — repository swallows failures
            runCatching { crowdMonitorRepository.refresh() }
        }
    }

    fun refresh() {
        refreshTick.value = refreshTick.value + 1
        viewModelScope.launch {
            runCatching { crowdMonitorRepository.refresh(force = true) }
        }
    }

    fun toggleFavorite(poolId: String) {
        viewModelScope.launch {
            favoritePoolsStore.toggleFavorite(poolId)
        }
    }

    private fun buildState(
        favoriteIds: Set<String>,
        minMinutesUntilClose: Long,
        crowd: CrowdMonitorSnapshot,
    ): PoolRecommendationsUiState {
        val zone = clockProvider.zoneId()
        val nowZ = ZonedDateTime.ofInstant(clockProvider.now(), zone)
        val date = nowZ.toLocalDate()
        val time = nowZ.toLocalTime()
        val catalog = poolsRepository.getCatalog()
        val pools = catalog.selectablePools()
        val all = OpenPoolRecommender.recommend(
            pools = pools,
            date = date,
            now = time,
            favoriteIds = favoriteIds,
            seasonYear = catalog.seasonYear ?: date.year,
            minMinutesUntilClose = minMinutesUntilClose,
            includeClosingSoon = true,
        )
        val recommended = all.filter { it.isRecommended }
        val closingSoon = all.filter { it.isClosingSoon }
        val openIds = all.map { it.pool.id }.toSet()
        val favoritesOffline = pools
            .filter { it.id in favoriteIds && it.id !in openIds }
            .sortedBy { it.name }

        return PoolRecommendationsUiState(
            asOfDate = date,
            asOfTime = time,
            topPick = OpenPoolRecommender.topPick(all),
            recommended = recommended,
            closingSoon = closingSoon,
            favoritesOffline = favoritesOffline,
            allPools = pools.sortedWith(
                compareByDescending<Pool> { it.id in favoriteIds }
                    .thenBy { it.name },
            ),
            favoriteIds = favoriteIds,
            minMinutes = minMinutesUntilClose,
            crowd = crowd,
        )
    }
}

@Composable
fun PoolRecommendationsScreen(
    onBack: () -> Unit,
    onOpenPool: (poolId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PoolRecommendationsViewModel = hiltViewModel(),
) {
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
        Text(
            text = stringResource(R.string.recommend_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.recommend_subtitle,
                state.minMinutes,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.asOfTime?.let { t ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.recommend_as_of, t.format(timeFmt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.refresh() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.recommend_refresh))
        }

        // Top pick hero
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.recommend_top_heading),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val top = state.topPick
        if (top == null) {
            Text(
                text = stringResource(R.string.recommend_none_open),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            RecommendationCard(
                rec = top,
                timeFmt = timeFmt,
                highlighted = true,
                crowd = state.crowd,
                onOpenPool = { onOpenPool(top.pool.id) },
                onToggleFavorite = { viewModel.toggleFavorite(top.pool.id) },
            )
        }

        // Recommended list (favorites first already)
        if (state.recommended.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.recommend_open_heading),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            state.recommended.forEach { rec ->
                RecommendationCard(
                    rec = rec,
                    timeFmt = timeFmt,
                    highlighted = false,
                    crowd = state.crowd,
                    onOpenPool = { onOpenPool(rec.pool.id) },
                    onToggleFavorite = { viewModel.toggleFavorite(rec.pool.id) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (state.closingSoon.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.recommend_closing_soon_heading),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.recommend_closing_soon_hint, state.minMinutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            state.closingSoon.forEach { rec ->
                RecommendationCard(
                    rec = rec,
                    timeFmt = timeFmt,
                    highlighted = false,
                    crowd = state.crowd,
                    onOpenPool = { onOpenPool(rec.pool.id) },
                    onToggleFavorite = { viewModel.toggleFavorite(rec.pool.id) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (state.favoritesOffline.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.recommend_favorites_closed_heading),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            state.favoritesOffline.forEach { pool ->
                FavoriteClosedRow(
                    pool = pool,
                    isFavorite = true,
                    crowd = state.crowd,
                    onOpenPool = { onOpenPool(pool.id) },
                    onToggleFavorite = { viewModel.toggleFavorite(pool.id) },
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // Manage favorites among all pools
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.recommend_favorites_manage),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.recommend_favorites_manage_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        state.allPools.forEach { pool ->
            val fav = pool.id in state.favoriteIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenPool(pool.id) },
                ) {
                    Text(
                        text = pool.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = pool.kind,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilterChip(
                    selected = fav,
                    onClick = { viewModel.toggleFavorite(pool.id) },
                    label = {
                        Text(
                            if (fav) {
                                stringResource(R.string.recommend_favorite_on)
                            } else {
                                stringResource(R.string.recommend_favorite_off)
                            },
                        )
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun RecommendationCard(
    rec: OpenPoolRecommender.Recommendation,
    timeFmt: DateTimeFormatter,
    highlighted: Boolean,
    crowd: CrowdMonitorSnapshot,
    onOpenPool: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenPool),
                ) {
                    Text(
                        text = rec.pool.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = buildString {
                            append(rec.open.format(timeFmt))
                            append("–")
                            append(rec.close.format(timeFmt))
                            rec.condition?.let { cond ->
                                append(" · ")
                                append(
                                    when (cond) {
                                        "any_weather" -> stringResource(R.string.session_hours_any_weather)
                                        "fair_weather" -> stringResource(R.string.session_hours_fair_weather)
                                        else -> cond
                                    },
                                )
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilterChip(
                    selected = rec.isFavorite,
                    onClick = onToggleFavorite,
                    label = {
                        Text(
                            if (rec.isFavorite) {
                                stringResource(R.string.recommend_favorite_on)
                            } else {
                                stringResource(R.string.recommend_favorite_off)
                            },
                        )
                    },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.recommend_closes_in,
                    formatMinutes(rec.minutesUntilClose),
                    rec.close.format(timeFmt),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (rec.isClosingSoon) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            if (rec.isFavorite && rec.pool.crowdMonitorUid != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OccupancyIconsRow(
                    connection = crowd.connection,
                    occupancy = crowd.forUid(rec.pool.crowdMonitorUid),
                    compact = false,
                )
            }
            rec.pool.description?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun FavoriteClosedRow(
    pool: Pool,
    isFavorite: Boolean,
    crowd: CrowdMonitorSnapshot,
    onOpenPool: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenPool),
            ) {
                Text(
                    text = pool.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.recommend_not_open_now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onToggleFavorite) {
                Text(
                    if (isFavorite) {
                        stringResource(R.string.recommend_favorite_on)
                    } else {
                        stringResource(R.string.recommend_favorite_off)
                    },
                )
            }
        }
        if (isFavorite && pool.crowdMonitorUid != null) {
            Spacer(modifier = Modifier.height(4.dp))
            OccupancyIconsRow(
                connection = crowd.connection,
                occupancy = crowd.forUid(pool.crowdMonitorUid),
                compact = true,
            )
        }
    }
}

private fun formatMinutes(minutes: Long): String {
    if (minutes < 60) return "${minutes}m"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0L) "${h}h" else "${h}h ${m}m"
}
