package com.agniv.hisaab.feature.udhaar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.FieldBox
import com.agniv.hisaab.core.designsystem.component.HBusyButton
import com.agniv.hisaab.core.designsystem.component.HButton
import com.agniv.hisaab.core.designsystem.component.HOutlinedButton
import com.agniv.hisaab.core.designsystem.component.InitialAvatar
import com.agniv.hisaab.core.designsystem.component.InlineError
import com.agniv.hisaab.core.designsystem.component.KinlyTextField
import com.agniv.hisaab.core.designsystem.component.PillTabs
import com.agniv.hisaab.core.designsystem.motion.popIn
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.UdhaarDirection
import com.agniv.hisaab.core.domain.usecase.UdhaarItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private fun LocalDate.text(locale: java.util.Locale) = format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))

private enum class SettleStep { CHOOSE, PARTIAL, DONE }

/**
 * Board "Settle Udhaar" → "Record Partial Payment" → "Marked as Settled". The first step also lists earlier part
 * payments (each can be undone) and offers Edit / Delete, so nothing the old detail sheet did is lost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettleFlow(
    item: UdhaarItem,
    onEvent: (UdhaarEvent) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(SettleStep.CHOOSE) }
    var settleAll by rememberSaveable { mutableStateOf(true) }
    var payText by rememberSaveable { mutableStateOf("") }
    val e = item.udhaar
    val locale = LocalConfiguration.current.locales[0]
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
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            if (step == SettleStep.DONE) {
                Settled(item, onDone = onDismiss)
                return@Column
            }
            SheetHeader(
                stringResource(if (step == SettleStep.PARTIAL) R.string.udhaar_partial_title else R.string.udhaar_settle_title),
                onDismiss,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                    .padding(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                InitialAvatar(e.person)
                Column {
                    Text(e.person, style = MaterialTheme.typography.titleSmall)
                    e.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Fact(R.string.udhaar_total, e.amount.formatted())
                Fact(R.string.udhaar_paid_so_far, item.paid.formatted())
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
                        .padding(horizontal = Spacing.m, vertical = Spacing.s),
                ) {
                    Text(
                        stringResource(R.string.udhaar_remaining),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        item.remaining.formatted(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            if (step == SettleStep.CHOOSE) {
                if (!item.settled) {
                    Choice(
                        selected = settleAll,
                        title = stringResource(R.string.udhaar_mark_settled),
                        hint = stringResource(R.string.udhaar_settle_hint, item.remaining.formatted()),
                        onClick = { settleAll = true },
                    )
                    Choice(
                        selected = !settleAll,
                        title = stringResource(R.string.udhaar_record_partial),
                        hint = stringResource(R.string.udhaar_partial_hint),
                        onClick = { settleAll = false },
                    )
                    HButton(
                        onClick = {
                            if (settleAll) {
                                onEvent(UdhaarEvent.Settle(e.id))
                                step = SettleStep.DONE
                            } else {
                                step = SettleStep.PARTIAL
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.udhaar_continue)) }
                }
                Payments(item, locale, onEvent)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.udhaar_edit)) }
                }
            } else {
                KinlyTextField(
                    value = payText,
                    onValueChange = { payText = it },
                    label = stringResource(R.string.udhaar_pay_amount),
                    leadingIcon = Icons.Filled.CurrencyRupee,
                    keyboardType = KeyboardType.Decimal,
                    isError = tooMuch,
                )
                if (tooMuch) InlineError(stringResource(R.string.udhaar_pay_too_much, item.remaining.formatted()))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    HOutlinedButton(onClick = { step = SettleStep.CHOOSE }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cancel))
                    }
                    HButton(
                        onClick = {
                            payAmount?.let { onEvent(UdhaarEvent.Pay(e.id, it)) }
                            payText = ""
                            // Paying exactly what is left settles it; show the same confirmation as "Mark as settled".
                            step = if (payAmount == item.remaining) SettleStep.DONE else SettleStep.CHOOSE
                        },
                        enabled = payAmount != null && !tooMuch,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.udhaar_save)) }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(
    title: String,
    onClose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.udhaar_close)) }
    }
}

@Composable
private fun Choice(
    selected: Boolean,
    title: String,
    hint: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = Spacing.s)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Payments(
    item: UdhaarItem,
    locale: java.util.Locale,
    onEvent: (UdhaarEvent) -> Unit,
) {
    if (item.payments.isEmpty()) return
    HorizontalDivider()
    Text(stringResource(R.string.udhaar_payments), style = MaterialTheme.typography.titleSmall)
    item.payments.forEach { p ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                p.paidOn.text(locale) + " • " + p.amount.formatted(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { onEvent(UdhaarEvent.RemovePayment(p)) }) { Text(stringResource(R.string.udhaar_undo)) }
        }
    }
}

/** Board "Marked as Settled": a check that pops in, one sentence, Done. */
@Composable
private fun Settled(
    item: UdhaarItem,
    onDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Box(
            Modifier
                .size(104.dp)
                .popIn()
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(56.dp))
        }
        Text(stringResource(R.string.udhaar_marked_settled), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.udhaar_marked_settled_body, item.udhaar.person, item.udhaar.amount.formatted()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        HButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.udhaar_done)) }
    }
}

@Composable
private fun Fact(
    label: Int,
    value: String,
) {
    Row(Modifier.padding(horizontal = Spacing.m)) {
        Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** Board "Add Udhaar Entry": type pills, person, amount, date, note; plus due date and "only me" the app already had. */
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
            SheetHeader(stringResource(if (state.editingId == null) R.string.udhaar_add_title else R.string.udhaar_edit_title), onDismiss)
            Text(stringResource(R.string.udhaar_type), style = MaterialTheme.typography.titleSmall)
            PillTabs(
                options = listOf("↑ " + stringResource(R.string.udhaar_tab_gave), "↓ " + stringResource(R.string.udhaar_tab_owe)),
                selected = if (state.direction == UdhaarDirection.I_OWE) 1 else 0,
                onSelect = {
                    viewModel.onEvent(AddUdhaarEvent.Direction(if (it == 0) UdhaarDirection.THEY_OWE_ME else UdhaarDirection.I_OWE))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            KinlyTextField(
                value = state.person,
                onValueChange = { viewModel.onEvent(AddUdhaarEvent.Person(it)) },
                label = stringResource(R.string.udhaar_person),
                leadingIcon = Icons.Outlined.Person,
                isError = state.error == UdhaarField.PERSON,
            )
            KinlyTextField(
                value = state.amount,
                onValueChange = { viewModel.onEvent(AddUdhaarEvent.Amount(it)) },
                label = stringResource(R.string.udhaar_amount),
                leadingIcon = Icons.Filled.CurrencyRupee,
                keyboardType = KeyboardType.Decimal,
                isError = state.error == UdhaarField.AMOUNT,
            )
            FieldBox(
                value = state.date.text(locale),
                label = stringResource(R.string.udhaar_date),
                leadingIcon = Icons.Outlined.CalendarToday,
                onClick = { pickDate = false },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldBox(
                    value = state.dueDate?.text(locale) ?: stringResource(R.string.udhaar_no_due),
                    label = stringResource(R.string.udhaar_due),
                    leadingIcon = Icons.Outlined.EventAvailable,
                    onClick = { pickDate = true },
                    modifier = Modifier.weight(1f),
                )
                if (state.dueDate != null) {
                    TextButton(onClick = { viewModel.onEvent(AddUdhaarEvent.Due(null)) }) { Text(stringResource(R.string.udhaar_clear)) }
                }
            }
            KinlyTextField(
                value = state.note,
                onValueChange = { viewModel.onEvent(AddUdhaarEvent.Note(it)) },
                label = stringResource(R.string.udhaar_note),
                leadingIcon = Icons.AutoMirrored.Outlined.Notes,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.udhaar_only_me), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.udhaar_only_me_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.onlyMe, onCheckedChange = { viewModel.onEvent(AddUdhaarEvent.OnlyMe(it)) })
            }
            ErrorLine(state.error)
            HBusyButton(
                text = stringResource(R.string.udhaar_save),
                busyText = stringResource(R.string.udhaar_saving),
                busy = state.saving,
                onClick = { viewModel.onEvent(AddUdhaarEvent.Save) },
                modifier = Modifier.fillMaxWidth(),
            )
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
    InlineError(stringResource(res))
}
