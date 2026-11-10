package com.agniv.hisaab.feature.expenses.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.Recurring
import com.agniv.hisaab.core.domain.usecase.RecurrenceSchedule
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.add.labelRes
import com.agniv.hisaab.feature.expenses.ui.emoji
import com.agniv.hisaab.feature.expenses.ui.label
import java.time.format.DateTimeFormatter

/** Everything that repeats, with the next date and a way to stop (delete, with a confirm). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringSheet(
    onDismiss: () -> Unit,
    viewModel: RecurringViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Recurring?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(stringResource(R.string.repeating_title), style = MaterialTheme.typography.headlineSmall)
            if (state.items.isEmpty()) {
                Text(
                    stringResource(R.string.repeating_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.xl),
                )
            }
            state.items.forEach { item ->
                RecurringRow(item, state, onDelete = { toDelete = item })
                HorizontalDivider()
            }
        }
    }
    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.repeating_delete_title)) },
            text = { Text(stringResource(R.string.repeating_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item.id)
                    toDelete = null
                }) { Text(stringResource(R.string.repeating_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun RecurringRow(
    item: Recurring,
    state: RecurringUiState,
    onDelete: () -> Unit,
) {
    val category = state.categories[item.categoryId]
    val next = RecurrenceSchedule.nextDate(item).format(DateTimeFormatter.ofPattern("d MMM", LocalConfiguration.current.locales[0]))
    Row(Modifier.padding(vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val title = (category?.let { "${it.emoji()} ${it.label()}" } ?: "") + (item.note?.let { " • $it" } ?: "")
            Text(title.ifBlank { stringResource(R.string.repeating_untitled) }, style = MaterialTheme.typography.titleMedium)
            Text(
                item.amount.formatted() + " • " + stringResource(item.rule.labelRes()) + " • " +
                    stringResource(R.string.repeating_next, next),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.canDelete(item)) {
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.repeating_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
