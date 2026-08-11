package com.swimweek.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swimweek.app.data.PreferencesStore
import com.swimweek.app.data.SessionPlaceStore
import com.swimweek.app.data.UserPreferences
import com.swimweek.app.domain.DistanceUnit
import com.swimweek.app.domain.WeekStatPoint
import com.swimweek.app.domain.WeeklySwimSummary
import com.swimweek.app.health.SwimDistanceRepository
import com.swimweek.app.sync.SyncReason
import com.swimweek.app.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val summary: WeeklySwimSummary? = null,
    val preferences: UserPreferences = UserPreferences(),
    val weekStats: List<WeekStatPoint> = emptyList(),
    /** sessionId → pool display name for list rows */
    val sessionPlaces: Map<String, String> = emptyMap(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val errorMessage: String? = null,
) {
    val distanceUnit: DistanceUnit get() = preferences.distanceUnit
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SwimDistanceRepository,
    private val preferencesStore: PreferencesStore,
    private val sessionPlaceStore: SessionPlaceStore,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.summaryFlow,
        preferencesStore.preferencesFlow,
        repository.weeklyHistoryFlow,
        sessionPlaceStore.placesFlow,
        combine(refreshing, error) { r, e -> r to e },
    ) { summary, prefs, history, places, refreshErr ->
        val (isRefreshing, err) = refreshErr
        HomeUiState(
            summary = summary,
            preferences = prefs,
            weekStats = history,
            sessionPlaces = places.mapValues { it.value.poolName },
            loading = summary == null && isRefreshing,
            refreshing = isRefreshing,
            errorMessage = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(loading = true),
    )

    init {
        syncScheduler.ensureScheduled()
        refresh(reason = SyncReason.APP_FOREGROUND)
    }

    fun refresh(reason: SyncReason = SyncReason.MANUAL) {
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            try {
                repository.sync(reason)
                syncScheduler.ensureScheduled()
            } catch (e: Exception) {
                error.value = e.message ?: e::class.java.simpleName
            } finally {
                refreshing.value = false
            }
        }
    }
}
