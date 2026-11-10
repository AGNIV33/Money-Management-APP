package com.agniv.hisaab.feature.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.usecase.GoalItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private fun LocalDate.text(locale: java.util.Locale) = format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))

/** One goal: the jar, savings so far, add savings, undo a wrong entry, edit, delete (creator or admin, with a confirm). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalDetailSheet(
    item: GoalItem,
    canDelete: Boolean,
    onEvent: (GoalsEvent) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val g = item.goal
    val locale = LocalConfiguration.current.locales[0]
    var addText by rememberSaveable { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val amount = Money.parse(addText)?.takeIf { it.paise > 0 }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(g.name, style = MaterialTheme.typography.headlineSmall)
            GoalJar(item.fraction)
            Text(
                stringResource(R.string.goals_saved_of, item.saved.formatted(), g.target.formatted()),
                style = MaterialTheme.typography.titleMedium,
                color = HisaabTheme.extendedColors.success,
            )
            if (item.reached) {
                Text("🎉 " + stringResource(R.string.goals_reached), color = HisaabTheme.extendedColors.success)
            } else {
                Text(stringResource(R.string.goals_left, item.remaining.formatted()))
                item.perMonthNeeded?.let {
                    Text(
                        stringResource(R.string.goals_per_month, it.formatted()),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            g.deadline?.let { Text(stringResource(R.string.goals_by, it.text(locale)), color = MaterialTheme.colorScheme.onSurfaceVariant) }

            if (item.contributions.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.goals_entries), style = MaterialTheme.typography.titleMedium)
                item.contributions.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.date.text(locale) + " • " + c.amount.formatted(), modifier = Modifier.weight(1f))
                        TextButton(onClick = { onEvent(GoalsEvent.Remove(c)) }) { Text(stringResource(R.string.goals_undo)) }
                    }
                }
            }
            HorizontalDivider()
            OutlinedTextField(
                value = addText,
                onValueChange = { addText = it },
                label = { Text(stringResource(R.string.goals_add_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    amount?.let { onEvent(GoalsEvent.Add(g.id, it)) }
                    addText = ""
                },
                enabled = amount != null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
            ) { Text(stringResource(R.string.goals_add_savings)) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.goals_edit)) }
                if (canDelete) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.goals_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.goals_delete_title)) },
            text = { Text(stringResource(R.string.goals_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onEvent(GoalsEvent.Delete(g.id))
                    onDismiss()
                }) { Text(stringResource(R.string.goals_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddGoalSheet(
    onDismiss: () -> Unit,
    editId: String?,
    instanceKey: String,
    viewModel: AddGoalViewModel = hiltViewModel(key = "add-goal-$instanceKey"),
) {
    LaunchedEffect(editId) { viewModel.start(editId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.savedEvents.collect { onDismiss() } }
    var pickDate by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                stringResource(if (state.editingId == null) R.string.goals_new_title else R.string.goals_edit_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.goals_name)) },
                isError = state.error == GoalField.NAME,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.target,
                onValueChange = viewModel::setTarget,
                label = { Text(stringResource(R.string.goals_target)) },
                isError = state.error == GoalField.TARGET,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.deadline?.let {
                        stringResource(
                            R.string.goals_by,
                            it.text(locale),
                        )
                    } ?: stringResource(R.string.goals_no_deadline),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { pickDate = true }) { Text(stringResource(R.string.goals_set_date)) }
                if (state.deadline !=
                    null
                ) {
                    TextButton(onClick = { viewModel.setDeadline(null) }) { Text(stringResource(R.string.goals_clear)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.goals_only_me), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.goals_only_me_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.onlyMe, onCheckedChange = viewModel::setOnlyMe)
            }
            ErrorLine(state.error)
            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
            ) { Text(stringResource(R.string.goals_save)) }
        }
    }
    if (pickDate) {
        GoalDateDialog(initial = state.deadline ?: LocalDate.now(), onPick = viewModel::setDeadline, onDismiss = { pickDate = false })
    }
}

@Composable
private fun ErrorLine(error: GoalField?) {
    val res =
        when (error) {
            null -> return
            GoalField.NAME -> R.string.goals_err_name
            GoalField.TARGET -> R.string.goals_err_target
            GoalField.DEADLINE -> R.string.goals_err_deadline
            GoalField.SAVE -> R.string.goals_err_save
        }
    Text(stringResource(res), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDateDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // DatePicker works in UTC millis.
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state = picker) }
}
