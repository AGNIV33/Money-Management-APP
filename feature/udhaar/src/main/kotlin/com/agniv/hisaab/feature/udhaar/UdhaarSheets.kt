package com.agniv.hisaab.feature.udhaar

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.UdhaarDirection
import com.agniv.hisaab.core.domain.usecase.UdhaarItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private fun LocalDate.text(locale: java.util.Locale) = format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))

/** One entry: payments, add a payment, mark settled, edit, delete (creator or admin, with a confirm). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UdhaarDetailSheet(
    item: UdhaarItem,
    canDelete: Boolean,
    onEvent: (UdhaarEvent) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val e = item.udhaar
    val locale = LocalConfiguration.current.locales[0]
    var payText by rememberSaveable { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val payAmount = Money.parse(payText)?.takeIf { it.paise > 0 }
    val tooMuch = payAmount != null && payAmount > item.remaining

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
            Text(e.person, style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(if (e.direction == UdhaarDirection.THEY_OWE_ME) R.string.udhaar_dir_get else R.string.udhaar_dir_give),
                color = directionColor(e.direction),
            )
            Fact(R.string.udhaar_total, e.amount.formatted())
            Fact(R.string.udhaar_paid, item.paid.formatted())
            Fact(R.string.udhaar_left, item.remaining.formatted())
            Fact(R.string.udhaar_date, e.date.text(locale))
            e.dueDate?.let { Fact(R.string.udhaar_due, it.text(locale)) }
            e.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            if (item.payments.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.udhaar_payments), style = MaterialTheme.typography.titleMedium)
                item.payments.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.paidOn.text(locale) + " • " + p.amount.formatted(), modifier = Modifier.weight(1f))
                        TextButton(onClick = { onEvent(UdhaarEvent.RemovePayment(p)) }) { Text(stringResource(R.string.udhaar_undo)) }
                    }
                }
            }

            if (!item.settled) {
                HorizontalDivider()
                OutlinedTextField(
                    value = payText,
                    onValueChange = { payText = it },
                    label = { Text(stringResource(R.string.udhaar_pay_label)) },
                    isError = tooMuch,
                    supportingText = { if (tooMuch) Text(stringResource(R.string.udhaar_pay_too_much, item.remaining.formatted())) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            payAmount?.let { onEvent(UdhaarEvent.Pay(e.id, it)) }
                            payText = ""
                        },
                        enabled = payAmount != null && !tooMuch,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(52.dp),
                    ) { Text(stringResource(R.string.udhaar_add_payment)) }
                    Button(
                        onClick = { onEvent(UdhaarEvent.Settle(e.id)) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(52.dp),
                    ) { Text("✓ " + stringResource(R.string.udhaar_mark_settled)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.udhaar_edit)) }
                if (canDelete) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.udhaar_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.udhaar_delete_title)) },
            text = { Text(stringResource(R.string.udhaar_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onEvent(UdhaarEvent.Delete(e.id))
                    onDismiss()
                }) { Text(stringResource(R.string.udhaar_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Fact(
    label: Int,
    value: String,
) {
    Row {
        Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddUdhaarSheet(
    onDismiss: () -> Unit,
    editId: String?,
    instanceKey: String,
    direction: UdhaarDirection?,
    viewModel: AddUdhaarViewModel = hiltViewModel(key = "add-udhaar-$instanceKey"),
) {
    LaunchedEffect(editId) { viewModel.start(editId, direction ?: UdhaarDirection.THEY_OWE_ME) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.savedEvents.collect { onDismiss() } }
    var pickDate by remember { mutableStateOf<Boolean?>(null) } // false = entry date, true = due date
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
                stringResource(if (state.editingId == null) R.string.udhaar_add_title else R.string.udhaar_edit_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FilterChip(
                    selected = state.direction == UdhaarDirection.THEY_OWE_ME,
                    onClick = { viewModel.onEvent(AddUdhaarEvent.Direction(UdhaarDirection.THEY_OWE_ME)) },
                    label = { Text(stringResource(R.string.udhaar_dir_get)) },
                )
                FilterChip(
                    selected = state.direction == UdhaarDirection.I_OWE,
                    onClick = { viewModel.onEvent(AddUdhaarEvent.Direction(UdhaarDirection.I_OWE)) },
                    label = { Text(stringResource(R.string.udhaar_dir_give)) },
                )
            }
            OutlinedTextField(
                value = state.person,
                onValueChange = { viewModel.onEvent(AddUdhaarEvent.Person(it)) },
                label = { Text(stringResource(R.string.udhaar_person)) },
                isError = state.error == UdhaarField.PERSON,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.amount,
                onValueChange = { viewModel.onEvent(AddUdhaarEvent.Amount(it)) },
                label = { Text(stringResource(R.string.udhaar_amount)) },
                isError = state.error == UdhaarField.AMOUNT,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.udhaar_date_line, state.date.text(locale)), modifier = Modifier.weight(1f))
                TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.udhaar_change)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.dueDate?.let { stringResource(R.string.udhaar_due_line, it.text(locale)) }
                        ?: stringResource(R.string.udhaar_no_due),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { pickDate = true }) { Text(stringResource(R.string.udhaar_set_due)) }
                if (state.dueDate !=
                    null
                ) {
                    TextButton(
                        onClick = { viewModel.onEvent(AddUdhaarEvent.Due(null)) },
                    ) { Text(stringResource(R.string.udhaar_clear)) }
                }
            }
            OutlinedTextField(
                value = state.note,
                onValueChange = { viewModel.onEvent(AddUdhaarEvent.Note(it)) },
                label = { Text(stringResource(R.string.udhaar_note)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.udhaar_only_me), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.udhaar_only_me_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.onlyMe, onCheckedChange = { viewModel.onEvent(AddUdhaarEvent.OnlyMe(it)) })
            }
            ErrorLine(state.error)
            Button(
                onClick = { viewModel.onEvent(AddUdhaarEvent.Save) },
                enabled = !state.saving,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
            ) { Text(stringResource(R.string.udhaar_save)) }
        }
    }

    pickDate?.let { forDue ->
        UdhaarDateDialog(
            initial = if (forDue) state.dueDate ?: state.date else state.date,
            onPick = { d -> viewModel.onEvent(if (forDue) AddUdhaarEvent.Due(d) else AddUdhaarEvent.Date(d)) },
            onDismiss = { pickDate = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UdhaarDateDialog(
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

@Composable
private fun ErrorLine(error: UdhaarField?) {
    val res =
        when (error) {
            null -> return
            UdhaarField.PERSON -> R.string.udhaar_err_person
            UdhaarField.AMOUNT -> R.string.udhaar_err_amount
            UdhaarField.NOTE -> R.string.udhaar_err_note
            UdhaarField.SAVE -> R.string.udhaar_err_save
        }
    Text(stringResource(res), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}
