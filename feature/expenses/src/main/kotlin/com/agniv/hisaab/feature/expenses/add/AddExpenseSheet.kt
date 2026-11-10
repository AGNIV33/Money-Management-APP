package com.agniv.hisaab.feature.expenses.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.component.HNumberPad
import com.agniv.hisaab.core.designsystem.theme.DisplayAmount
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.RecurRule
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.ui.emoji
import com.agniv.hisaab.feature.expenses.ui.label
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    onDismiss: () -> Unit,
    editId: String? = null,
    viewModel: AddExpenseViewModel = hiltViewModel(),
) {
    LaunchedEffect(editId) { viewModel.start(editId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.savedEvents.collect { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        AddExpenseContent(state = state, onEvent = viewModel::onEvent)
    }
}

@Composable
internal fun AddExpenseContent(
    state: AddExpenseUiState,
    onEvent: (AddExpenseEvent) -> Unit,
) {
    var pickingDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(
            stringResource(if (state.editingId == null) R.string.add_expense_title else R.string.edit_expense_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "₹" + state.amountText.ifEmpty { "0" },
            style = DisplayAmount,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(state.error)

        Text(stringResource(R.string.add_expense_category), style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            items(state.categories, key = { it.id }) { category ->
                FilterChip(
                    selected = category.id == state.categoryId,
                    onClick = { onEvent(AddExpenseEvent.PickCategory(category.id)) },
                    label = { Text("${category.emoji()} ${category.label()}") },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            FilterChip(
                selected = state.date == state.today,
                onClick = { onEvent(AddExpenseEvent.PickDate(state.today)) },
                label = { Text(stringResource(R.string.date_today)) },
            )
            FilterChip(
                selected = state.date == state.today.minusDays(1),
                onClick = { onEvent(AddExpenseEvent.PickDate(state.today.minusDays(1))) },
                label = { Text(stringResource(R.string.date_yesterday)) },
            )
            val otherDay = state.date != state.today && state.date != state.today.minusDays(1)
            FilterChip(
                selected = otherDay,
                onClick = { pickingDate = true },
                label = { Text(if (otherDay) state.date.toString() else stringResource(R.string.date_pick)) },
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            items(PayMode.entries, key = { it.name }) { mode ->
                FilterChip(
                    selected = mode == state.mode,
                    onClick = { onEvent(AddExpenseEvent.PickMode(mode)) },
                    label = { Text(stringResource(mode.labelRes())) },
                )
            }
        }

        if (state.editingId == null) {
            Text(stringResource(R.string.repeat_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                listOf(null, RecurRule.DAILY, RecurRule.WEEKLY, RecurRule.MONTHLY).forEach { rule ->
                    FilterChip(
                        selected = state.repeat == rule,
                        onClick = { onEvent(AddExpenseEvent.PickRepeat(rule)) },
                        label = { Text(stringResource(rule.labelRes())) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.note,
            onValueChange = { onEvent(AddExpenseEvent.EditNote(it)) },
            label = { Text(stringResource(R.string.add_expense_note)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.add_expense_only_me), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.add_expense_only_me_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.onlyMe, onCheckedChange = { onEvent(AddExpenseEvent.SetOnlyMe(it)) })
        }

        HNumberPad(
            onKey = { onEvent(AddExpenseEvent.Key(it)) },
            backspaceDescription = stringResource(R.string.keypad_backspace),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { onEvent(AddExpenseEvent.Save) },
            enabled = !state.saving,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
        ) {
            Text(stringResource(R.string.add_expense_save))
        }
        if (state.canDelete) {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.delete_expense), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_expense_title)) },
            text = { Text(stringResource(R.string.delete_expense_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onEvent(AddExpenseEvent.Delete)
                }) { Text(stringResource(R.string.delete_expense), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (pickingDate) {
        DateDialog(
            initial = state.date,
            today = state.today,
            onPick = {
                onEvent(AddExpenseEvent.PickDate(it))
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

@Composable
private fun ErrorText(error: AddError?) {
    if (error == null) return
    val text =
        when (error) {
            AddError.AMOUNT -> R.string.error_amount
            AddError.CATEGORY -> R.string.error_category
            AddError.NOTE -> R.string.error_note
            AddError.SAVE_FAILED -> R.string.error_save
        }
    Text(stringResource(text), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(
    initial: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // DatePicker works in UTC millis.
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates =
                object : androidx.compose.material3.SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toLocalDate() <= today
                },
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(it.toLocalDate()) } ?: onDismiss() }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

internal fun PayMode.labelRes(): Int =
    when (this) {
        PayMode.CASH -> R.string.mode_cash
        PayMode.UPI -> R.string.mode_upi
        PayMode.CARD -> R.string.mode_card
        PayMode.OTHER -> R.string.mode_other
    }

internal fun RecurRule?.labelRes(): Int =
    when (this) {
        null -> R.string.repeat_never
        RecurRule.DAILY -> R.string.repeat_daily
        RecurRule.WEEKLY -> R.string.repeat_weekly
        RecurRule.MONTHLY -> R.string.repeat_monthly
    }
