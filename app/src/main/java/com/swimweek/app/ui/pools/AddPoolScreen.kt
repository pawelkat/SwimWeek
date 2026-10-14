package com.swimweek.app.ui.pools

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swimweek.app.R
import com.swimweek.app.data.CustomPoolStore
import com.swimweek.app.domain.Pool
import com.swimweek.app.ui.theme.AmoledBlack
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

private enum class NewPoolKind(val stored: String, val labelRes: Int) {
    INDOOR("indoor", R.string.add_pool_kind_indoor),
    OUTDOOR("outdoor", R.string.add_pool_kind_outdoor),
    OTHER("other", R.string.add_pool_kind_other),
}

@HiltViewModel
class AddPoolViewModel @Inject constructor(
    private val customPoolStore: CustomPoolStore,
) : ViewModel() {
    fun save(name: String, kind: String, note: String, onSaved: (Pool) -> Unit) {
        val trimmed = name.trim().take(80)
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val pool = customPoolStore.add(
                name = trimmed,
                kind = kind,
                note = note.trim().take(120).ifBlank { null },
            )
            onSaved(pool)
        }
    }
}

@Composable
fun AddPoolForm(
    onBack: () -> Unit,
    onSaved: (Pool) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddPoolViewModel = hiltViewModel(),
) {
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(NewPoolKind.INDOOR) }
    var showNameError by remember { mutableStateOf(false) }

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
            text = stringResource(R.string.add_pool_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.add_pool_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                showNameError = false
            },
            label = { Text(stringResource(R.string.add_pool_name)) },
            placeholder = { Text(stringResource(R.string.add_pool_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = poolFieldColors(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.add_pool_kind),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NewPoolKind.entries.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = kind == option,
                        onClick = { kind = option },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = kind == option, onClick = { kind = option })
                Text(
                    text = stringResource(option.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.add_pool_note)) },
            placeholder = { Text(stringResource(R.string.add_pool_note_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = poolFieldColors(),
        )
        if (showNameError) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.add_pool_error_name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                if (name.trim().isEmpty()) {
                    showNameError = true
                } else {
                    viewModel.save(name, kind.stored, note, onSaved)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add_pool_save))
        }
    }
}

@Composable
private fun poolFieldColors() = TextFieldDefaults.colors(
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
