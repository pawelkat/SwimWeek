package com.swimweek.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.swimweek.app.R
import com.swimweek.app.data.PreferencesStore
import com.swimweek.app.data.UserPreferences
import com.swimweek.app.domain.DistanceUnit
import com.swimweek.app.sync.WidgetUpdater
import com.swimweek.app.ui.theme.AmoledBlack
import com.swimweek.app.util.LengthFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesStore: PreferencesStore,
    private val widgetUpdater: WidgetUpdater,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesStore.preferencesFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserPreferences(),
    )

    fun setUnit(unit: DistanceUnit) {
        viewModelScope.launch {
            preferencesStore.setDistanceUnit(unit)
            widgetUpdater.updateAll()
        }
    }

    fun setWeekStart(day: DayOfWeek) {
        viewModelScope.launch {
            preferencesStore.setWeekStart(day)
            widgetUpdater.updateAll()
        }
    }

    fun setWeeklyTargetFromDisplay(value: Double, unit: DistanceUnit) {
        viewModelScope.launch {
            val meters = if (value > 0.0) DistanceUnit.toMeters(unit, value) else 0.0
            preferencesStore.setWeeklyTargetMeters(meters)
            widgetUpdater.updateAll()
        }
    }

    fun clearWeeklyTarget() {
        viewModelScope.launch {
            preferencesStore.setWeeklyTargetMeters(0.0)
            widgetUpdater.updateAll()
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val unit = prefs.distanceUnit
    val displayTarget = if (prefs.weeklyTargetMeters > 0) {
        DistanceUnit.metersTo(unit, prefs.weeklyTargetMeters)
    } else {
        0.0
    }
    var targetText by remember { mutableStateOf("") }

    LaunchedEffect(displayTarget, unit) {
        targetText = if (displayTarget > 0) {
            formatTargetForEdit(displayTarget, unit)
        } else {
            ""
        }
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
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.settings_weekly_target),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_weekly_target_hint, unitLabel(unit)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = targetText,
            onValueChange = { targetText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.settings_target_label)) },
            suffix = { Text(unitLabel(unit)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedContainerColor = AmoledBlack,
                unfocusedContainerColor = AmoledBlack,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val parsed = targetText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    viewModel.setWeeklyTargetFromDisplay(parsed, unit)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.settings_save_target))
            }
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedButton(
                onClick = {
                    targetText = ""
                    viewModel.clearWeeklyTarget()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.settings_clear_target))
            }
        }
        if (prefs.weeklyTargetMeters > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.settings_target_stored,
                    LengthFormat.format(prefs.weeklyTargetMeters, unit),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.settings_units),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        DistanceUnit.entries.forEach { u ->
            RadioRow(
                label = when (u) {
                    DistanceUnit.METERS -> stringResource(R.string.unit_meters)
                    DistanceUnit.YARDS -> stringResource(R.string.unit_yards)
                    DistanceUnit.MILES -> stringResource(R.string.unit_miles)
                },
                selected = prefs.distanceUnit == u,
                onClick = { viewModel.setUnit(u) },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.settings_week_start),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        RadioRow(
            label = stringResource(R.string.week_monday),
            selected = prefs.weekStart == DayOfWeek.MONDAY,
            onClick = { viewModel.setWeekStart(DayOfWeek.MONDAY) },
        )
        RadioRow(
            label = stringResource(R.string.week_sunday),
            selected = prefs.weekStart == DayOfWeek.SUNDAY,
            onClick = { viewModel.setWeekStart(DayOfWeek.SUNDAY) },
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings_week_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private fun unitLabel(unit: DistanceUnit): String =
    when (unit) {
        DistanceUnit.METERS -> "m"
        DistanceUnit.YARDS -> "yd"
        DistanceUnit.MILES -> "mi"
    }

private fun formatTargetForEdit(value: Double, unit: DistanceUnit): String =
    when (unit) {
        DistanceUnit.MILES -> String.format(Locale.US, "%.2f", value)
        else -> value.roundToInt().toString()
    }
