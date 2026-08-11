package com.swimweek.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.swimweek.app.alert.PoolReminderNotifier
import com.swimweek.app.data.PreferencesStore
import com.swimweek.app.ui.diagnostics.DiagnosticsScreen
import com.swimweek.app.ui.home.HomeScreen
import com.swimweek.app.ui.navigation.AppDestination
import com.swimweek.app.ui.onboarding.OnboardingScreen
import com.swimweek.app.ui.pools.PoolDetailsScreen
import com.swimweek.app.ui.pools.PoolRecommendationsScreen
import com.swimweek.app.ui.session.SessionDetailsScreen
import com.swimweek.app.ui.settings.SettingsScreen
import com.swimweek.app.ui.theme.SwimWeekTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesStore: PreferencesStore,
) : ViewModel() {
    val onboardingCompleted: StateFlow<Boolean?> =
        preferencesStore.preferencesFlow
            .map { it.onboardingCompleted }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Deep-link / notification target observed by composition. */
    private val navigationIntentFlow = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigationIntentFlow.value = intent
        enableEdgeToEdge()
        setContent {
            SwimWeekTheme {
                val mainVm: MainViewModel = hiltViewModel()
                val onboardingDone by mainVm.onboardingCompleted.collectAsStateWithLifecycle()
                var destination by remember { mutableStateOf(AppDestination.HOME) }
                var selectedSessionId by remember { mutableStateOf<String?>(null) }
                var selectedPoolId by remember { mutableStateOf<String?>(null) }
                var sessionDetailsBack by remember {
                    mutableStateOf(AppDestination.HOME)
                }
                var poolDetailsBack by remember {
                    mutableStateOf(AppDestination.POOL_RECOMMENDATIONS)
                }
                val navIntent by navigationIntentFlow.collectAsStateWithLifecycle()

                LaunchedEffect(onboardingDone) {
                    when (onboardingDone) {
                        false -> destination = AppDestination.ONBOARDING
                        true -> {
                            if (destination == AppDestination.ONBOARDING) {
                                destination = AppDestination.HOME
                            }
                        }
                        null -> Unit
                    }
                }

                // Notification / deep link → pool recommendations (after onboarding)
                LaunchedEffect(navIntent, onboardingDone) {
                    if (onboardingDone != true) return@LaunchedEffect
                    val i = navIntent ?: return@LaunchedEffect
                    if (shouldOpenRecommendations(i)) {
                        destination = AppDestination.POOL_RECOMMENDATIONS
                        // Consume so rotation doesn't re-trigger oddly
                        i.removeExtra(PoolReminderNotifier.EXTRA_OPEN_RECOMMENDATIONS)
                        i.removeExtra(PoolReminderNotifier.EXTRA_FROM_ALERT)
                        navigationIntentFlow.value = null
                    }
                }

                fun openSession(sessionId: String, from: AppDestination) {
                    selectedSessionId = sessionId
                    sessionDetailsBack = from
                    destination = AppDestination.SESSION_DETAILS
                }

                fun openPoolDetails(poolId: String, from: AppDestination) {
                    selectedPoolId = poolId
                    poolDetailsBack = from
                    destination = AppDestination.POOL_DETAILS
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val pad = Modifier.padding(innerPadding)
                    when (destination) {
                        AppDestination.ONBOARDING,
                        AppDestination.ONBOARDING_REPLAY,
                        -> OnboardingScreen(
                            onFinished = { destination = AppDestination.HOME },
                            modifier = pad,
                        )
                        AppDestination.HOME -> HomeScreen(
                            onOpenOnboarding = { destination = AppDestination.ONBOARDING_REPLAY },
                            onOpenSettings = { destination = AppDestination.SETTINGS },
                            onOpenDiagnostics = { destination = AppDestination.DIAGNOSTICS },
                            onOpenSession = { openSession(it, AppDestination.HOME) },
                            onOpenPoolRecommendations = {
                                destination = AppDestination.POOL_RECOMMENDATIONS
                            },
                            modifier = pad,
                        )
                        AppDestination.SETTINGS -> SettingsScreen(
                            onBack = { destination = AppDestination.HOME },
                            modifier = pad,
                        )
                        AppDestination.DIAGNOSTICS -> DiagnosticsScreen(
                            onBack = { destination = AppDestination.HOME },
                            onOpenSession = { openSession(it, AppDestination.DIAGNOSTICS) },
                            modifier = pad,
                        )
                        AppDestination.SESSION_DETAILS -> {
                            val id = selectedSessionId
                            if (id == null) {
                                destination = sessionDetailsBack
                            } else {
                                SessionDetailsScreen(
                                    sessionId = id,
                                    onBack = { destination = sessionDetailsBack },
                                    modifier = pad,
                                )
                            }
                        }
                        AppDestination.POOL_RECOMMENDATIONS -> PoolRecommendationsScreen(
                            onBack = { destination = AppDestination.HOME },
                            onOpenPool = {
                                openPoolDetails(it, AppDestination.POOL_RECOMMENDATIONS)
                            },
                            modifier = pad,
                        )
                        AppDestination.POOL_DETAILS -> {
                            val id = selectedPoolId
                            if (id == null) {
                                destination = poolDetailsBack
                            } else {
                                PoolDetailsScreen(
                                    poolId = id,
                                    onBack = { destination = poolDetailsBack },
                                    modifier = pad,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navigationIntentFlow.value = intent
    }

    companion object {
        fun shouldOpenRecommendations(intent: Intent): Boolean {
            if (intent.getBooleanExtra(PoolReminderNotifier.EXTRA_OPEN_RECOMMENDATIONS, false)) {
                return true
            }
            if (intent.getBooleanExtra(PoolReminderNotifier.EXTRA_FROM_ALERT, false)) {
                return true
            }
            return intent.action == PoolReminderNotifier.ACTION_OPEN_RECOMMENDATIONS
        }
    }
}
