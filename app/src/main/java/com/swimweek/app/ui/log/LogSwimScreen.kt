package com.swimweek.app.ui.log

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.swimweek.app.R
import com.swimweek.app.data.CustomPoolStore
import com.swimweek.app.data.FavoritePoolsStore
import com.swimweek.app.data.PreferencesStore
import com.swimweek.app.data.ZurichPoolsRepository
import com.swimweek.app.domain.DistanceUnit
import com.swimweek.app.domain.ManualSwimKind
import com.swimweek.app.domain.Pool
import com.swimweek.app.domain.WeekRange
import com.swimweek.app.domain.withCustomPools
import com.swimweek.app.health.SwimDistanceRepository
import com.swimweek.app.ui.pools.AddPoolForm
import com.swimweek.app.ui.pools.PoolPickerField
import com.swimweek.app.ui.theme.AmoledBlack
import com.swimweek.app.util.ClockProvider
import com.swimweek.app.util.LengthFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val MAX_METERS = 100_000.0

sealed class LogSwimFailure {
    data object Metres : LogSwimFailure()
    data object PoolPick : LogSwimFailure()
    data object Place : LogSwimFailure()
    data object Future : LogSwimFailure()
    data object TooOld : LogSwimFailure()
    data class Save(val detail: String) : LogSwimFailure()
}

@HiltViewModel
class LogSwimViewModel @Inject constructor(
    private val repository: SwimDistanceRepository,
    private val preferencesStore: PreferencesStore,
    private val poolsRepository: ZurichPoolsRepository,
    private val customPoolStore: CustomPoolStore,
    private val favoritePoolsStore: FavoritePoolsStore,
    private val clock: ClockProvider,
) : ViewModel() {

    val distanceUnit: StateFlow<DistanceUnit> = preferencesStore.preferencesFlow
        .map { it.distanceUnit }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DistanceUnit.METERS)

    val pools: StateFlow<List<Pool>> = combine(
        customPoolStore.poolsFlow,
        favoritePoolsStore.favoriteIdsFlow,
    ) { custom, favorites ->
        poolsRepository.selectablePools().withCustomPools(custom, favorites)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteIds: StateFlow<Set<String>> = favoritePoolsStore.favoriteIdsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    fun initialDate(): LocalDate = LocalDate.now(clock.clock().withZone(clock.zoneId()))

    fun initialTime(): LocalTime =
        LocalTime.now(clock.clock().withZone(clock.zoneId())).withSecond(0).withNano(0)

    fun save(
        kind: ManualSwimKind,
        pool: Pool?,
        place: String,
        watchMissed: Boolean,
        metersText: String,
        date: LocalDate,
        time: LocalTime,
        onResult: (LogSwimFailure?) -> Unit,
    ) {
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            val failure = runCatching {
                validateAndStore(kind, pool, place, watchMissed, metersText, date, time)
            }.fold(
                onSuccess = { it },
                onFailure = { error ->
                    LogSwimFailure.Save(error.message ?: error.javaClass.simpleName)
                },
            )
            _saving.value = false
            onResult(failure)
        }
    }

    private suspend fun validateAndStore(
        kind: ManualSwimKind,
        pool: Pool?,
        place: String,
        watchMissed: Boolean,
        metersText: String,
        date: LocalDate,
        time: LocalTime,
    ): LogSwimFailure? {
        val raw = metersText.trim().replace(',', '.').toDoubleOrNull()
        if (raw == null || raw <= 0.0) return LogSwimFailure.Metres
        val unit = preferencesStore.get().distanceUnit
        val meters = DistanceUnit.toMeters(unit, raw)
        if (meters <= 0.0 || meters > MAX_METERS) return LogSwimFailure.Metres

        val (resolvedName, poolId) = when (kind) {
            ManualSwimKind.POOL -> {
                val chosen = pool ?: return LogSwimFailure.PoolPick
                chosen.name to chosen.id
            }
            ManualSwimKind.OPEN_WATER -> {
                val trimmed = place.trim().take(80)
                if (trimmed.isBlank()) return LogSwimFailure.Place
                trimmed to null
            }
        }

        val zone = clock.zoneId()
        val start = date.atTime(time).atZone(zone).toInstant()
        if (start.isAfter(clock.now().plusSeconds(5 * 60))) return LogSwimFailure.Future
        val prefs = preferencesStore.get()
        var oldest = WeekRange.current(zone, prefs.weekStart, clock.clock())
        repeat(SwimDistanceRepository.HISTORY_WEEKS - 1) {
            oldest = oldest.previous()
        }
        if (start.isBefore(oldest.start)) return LogSwimFailure.TooOld

        repository.addManualSwim(
            kind = kind,
            name = resolvedName,
            start = start,
            distanceMeters = meters,
            poolId = poolId,
            watchMissed = watchMissed,
        )
        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSwimScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LogSwimViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val unit by viewModel.distanceUnit.collectAsStateWithLifecycle()
    val pools by viewModel.pools.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()

    var kind by remember { mutableStateOf(ManualSwimKind.POOL) }
    var selectedPoolId by remember { mutableStateOf<String?>(null) }
    var place by remember { mutableStateOf("") }
    var watchMissed by remember { mutableStateOf(false) }
    var addingPool by remember { mutableStateOf(false) }
    var meters by remember { mutableStateOf("") }
    val initial = remember { viewModel.initialDate() to viewModel.initialTime() }
    var date by remember { mutableStateOf(initial.first) }
    var time by remember { mutableStateOf(initial.second) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    val timePickerState = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute,
        is24Hour = true,
    )
    val dateLabel = remember(date) {
        date.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()),
        )
    }
    val timeLabel = remember(time) {
        time.format(
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()),
        )
    }
    val selectedPool = pools.firstOrNull { it.id == selectedPoolId }

    if (addingPool) {
        AddPoolForm(
            onBack = { addingPool = false },
            onSaved = { pool ->
                selectedPoolId = pool.id
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
        TextButton(onClick = onDone) {
            Text(stringResource(R.string.nav_back))
        }
        Text(
            text = stringResource(R.string.log_swim_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))

        KindRow(
            label = stringResource(R.string.log_swim_kind_pool),
            selected = kind == ManualSwimKind.POOL,
            onClick = { kind = ManualSwimKind.POOL },
        )
        KindRow(
            label = stringResource(R.string.log_swim_kind_open_water),
            selected = kind == ManualSwimKind.OPEN_WATER,
            onClick = { kind = ManualSwimKind.OPEN_WATER },
        )

        Spacer(modifier = Modifier.height(12.dp))
        if (kind == ManualSwimKind.POOL) {
            PoolPickerField(
                pools = pools,
                favoriteIds = favoriteIds,
                selectedPool = selectedPool,
                fallbackName = null,
                onSelect = { selectedPoolId = it.id },
                onClear = { selectedPoolId = null },
                onAdd = { addingPool = true },
            )
        } else {
            OutlinedTextField(
                value = place,
                onValueChange = { place = it },
                label = { Text(stringResource(R.string.log_swim_place)) },
                placeholder = { Text(stringResource(R.string.log_swim_place_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = watchMissed,
                    onClick = { watchMissed = !watchMissed },
                    role = Role.Checkbox,
                )
                .padding(vertical = 4.dp),
        ) {
            Checkbox(
                checked = watchMissed,
                onCheckedChange = { watchMissed = it },
            )
            Text(
                text = stringResource(R.string.log_swim_watch_missed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = meters,
            onValueChange = { meters = it },
            label = {
                Text(
                    stringResource(
                        R.string.log_swim_metres,
                        LengthFormat.unitLabel(unit, 2.0),
                    ),
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { showDate = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.log_swim_date) + " · " + dateLabel)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showTime = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.log_swim_time) + " · " + timeLabel)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.log_swim_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        errorText?.let { err ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = err,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                errorText = null
                viewModel.save(
                    kind = kind,
                    pool = selectedPool,
                    place = place,
                    watchMissed = watchMissed,
                    metersText = meters,
                    date = date,
                    time = time,
                ) { failure ->
                    if (failure == null) {
                        onDone()
                    } else {
                        errorText = failureMessage(context, failure)
                    }
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (saving) {
                    stringResource(R.string.log_swim_saving)
                } else {
                    stringResource(R.string.log_swim_save)
                },
            )
        }
    }

    if (showDate) {
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDate = false
                    },
                ) {
                    Text(stringResource(R.string.log_swim_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) {
                    Text(stringResource(R.string.log_swim_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTime) {
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        showTime = false
                    },
                ) {
                    Text(stringResource(R.string.log_swim_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) {
                    Text(stringResource(R.string.log_swim_cancel))
                }
            },
            text = { TimePicker(state = timePickerState) },
        )
    }
}

@Composable
private fun KindRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
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

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
    focusedContainerColor = AmoledBlack,
    unfocusedContainerColor = AmoledBlack,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

private fun failureMessage(context: Context, failure: LogSwimFailure): String =
    when (failure) {
        LogSwimFailure.Metres -> context.getString(R.string.log_swim_error_metres)
        LogSwimFailure.PoolPick -> context.getString(R.string.log_swim_error_pool_pick)
        LogSwimFailure.Place -> context.getString(R.string.log_swim_error_place)
        LogSwimFailure.Future -> context.getString(R.string.log_swim_error_future)
        LogSwimFailure.TooOld -> context.getString(
            R.string.log_swim_error_old,
            SwimDistanceRepository.HISTORY_WEEKS,
        )
        is LogSwimFailure.Save -> context.getString(R.string.log_swim_error_save, failure.detail)
    }
