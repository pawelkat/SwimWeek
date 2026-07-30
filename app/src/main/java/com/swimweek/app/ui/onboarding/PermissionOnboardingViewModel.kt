package com.swimweek.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swimweek.app.health.HealthConnectAvailability
import com.swimweek.app.health.HealthConnectSdkStatus
import com.swimweek.app.health.HealthPermissionChecker
import com.swimweek.app.health.SwimPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionOnboardingUiState(
    val sdkStatus: HealthConnectSdkStatus = HealthConnectSdkStatus.UNAVAILABLE,
    val granted: Set<String> = emptySet(),
    val hasRequired: Boolean = false,
    val hasBackground: Boolean = false,
    val loading: Boolean = true,
    val lastError: String? = null,
) {
    val providerPackage: String get() = HealthConnectAvailabilityConstants.PROVIDER_PACKAGE
}

/** Avoid injecting Context into state; package name is stable. */
private object HealthConnectAvailabilityConstants {
    const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
}

@HiltViewModel
class PermissionOnboardingViewModel @Inject constructor(
    private val availability: HealthConnectAvailability,
    private val permissionChecker: HealthPermissionChecker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionOnboardingUiState())
    val uiState: StateFlow<PermissionOnboardingUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, lastError = null) }
            try {
                val status = availability.status()
                val granted = if (status == HealthConnectSdkStatus.AVAILABLE) {
                    permissionChecker.grantedPermissions()
                } else {
                    emptySet()
                }
                _uiState.update {
                    it.copy(
                        sdkStatus = status,
                        granted = granted,
                        hasRequired = SwimPermissions.required.all { p -> p in granted },
                        hasBackground = SwimPermissions.recommendedBackground.all { p -> p in granted },
                        loading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        lastError = e.message ?: e::class.java.simpleName,
                    )
                }
            }
        }
    }

    fun permissionsToRequest(): Set<String> = SwimPermissions.allRequested

    fun onPermissionsResult(granted: Set<String>) {
        _uiState.update {
            it.copy(
                granted = granted,
                hasRequired = SwimPermissions.required.all { p -> p in granted },
                hasBackground = SwimPermissions.recommendedBackground.all { p -> p in granted },
                loading = false,
            )
        }
    }
}
