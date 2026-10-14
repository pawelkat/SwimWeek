package com.swimweek.app.ui.pools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.swimweek.app.R
import com.swimweek.app.data.CustomPoolStore
import com.swimweek.app.domain.Pool

/**
 * Pool list shared by logging a swim and by session details.
 * The plus button opens the form for a pool that is not in the Zurich catalogue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolPickerField(
    pools: List<Pool>,
    favoriteIds: Set<String>,
    selectedPool: Pool?,
    fallbackName: String?,
    onSelect: (Pool) -> Unit,
    onClear: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = selectedPool?.name
                    ?: fallbackName
                    ?: stringResource(R.string.session_pool_unset),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.session_pool_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                textStyle = MaterialTheme.typography.titleMedium,
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.session_pool_unset),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {
                        onClear()
                        expanded = false
                    },
                )
                pools.forEach { pool ->
                    val isFavorite = pool.id in favoriteIds
                    val kindLabel = poolKindLabel(pool)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = if (isFavorite) "★ ${pool.name}" else pool.name,
                                )
                                Text(
                                    text = if (isFavorite) {
                                        stringResource(R.string.session_pool_favorite_kind, kindLabel)
                                    } else {
                                        kindLabel
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onSelect(pool)
                            expanded = false
                        },
                    )
                }
            }
        }
        IconButton(onClick = onAdd) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.pool_picker_add),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun poolKindLabel(pool: Pool): String {
    val kind = pool.kind.replaceFirstChar { it.uppercase() }
    val note = pool.description?.takeIf { pool.category == CustomPoolStore.CUSTOM_POOL_CATEGORY }
    return if (note.isNullOrBlank()) kind else "$kind · $note"
}
