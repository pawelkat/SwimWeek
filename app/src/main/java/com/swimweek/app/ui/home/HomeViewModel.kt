package com.swimweek.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swimweek.app.data.PreferencesStore
import com.swimweek.app.data.UserPreferences
import com.swimweek.app.domain.DistanceUnit
import com.swimweek.app.domain.WeeklySwimSummary
import com.swimweek.app.health.SwimDistanceRepository
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
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.summaryFlow,
        preferencesStore.preferencesFlow,
        refreshing,
        error,
    ) { summary, prefs, isRefreshing, err ->
        HomeUiState(
            summary = summary,
            preferences = prefs,
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
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            try {
                repository.refreshWeeklySummary(forceFull = true)
            } catch (e: Exception) {
                error.value = e.message ?: e::class.java.simpleName
            } finally {
                refreshing.value = false
            }
        }
    }
}
