package com.agniv.hisaab.feature.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.LoanType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLoanSheet(
    onDismiss: () -> Unit,
    editId: String? = null,
    onSaved: () -> Unit = {},
    /** Different for each opening, so a new sheet never shows the last one's typing. */
    instanceKey: String = editId.orEmpty(),
    viewModel: AddLoanViewModel = hiltViewModel(key = "add-loan-$instanceKey"),
) {
    LaunchedEffect(editId) { viewModel.start(editId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.savedEvents.collect {
            onSaved()
            onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        AddLoanContent(state, viewModel::onEvent)
    }
}

@Composable
private fun AddLoanContent(
    state: AddLoanUiState,
    onEvent: (AddLoanEvent) -> Unit,
) {
    var pickingDate by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding()
                .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(
            stringResource(if (state.editingId == null) R.string.add_loan_title else R.string.edit_loan_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        // Only for a new loan: an existing one is already filled in.
        if (state.editingId == null) AiFillCard(state, onEvent)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            items(LoanType.entries, key = { it.name }) { type ->
                FilterChip(
                    selected = type == state.type,
                    onClick = { onEvent(AddLoanEvent.Type(type)) },
                    label = { Text("${type.emoji()} ${stringResource(type.labelRes())}") },
                )
            }
        }
        Field(state.lender, R.string.add_loan_lender, state.error == LoanField.LENDER, KeyboardType.Text) {
            onEvent(AddLoanEvent.Lender(it))
        }
        Field(state.amount, R.string.add_loan_amount, state.error == LoanField.AMOUNT, KeyboardType.Decimal) {
            onEvent(AddLoanEvent.Amount(it))
        }
        Field(state.rate, R.string.add_loan_rate, state.error == LoanField.RATE, KeyboardType.Decimal) {
            onEvent(AddLoanEvent.Rate(it))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Column(Modifier.weight(1f)) {
                Field(state.tenure, R.string.add_loan_tenure, state.error == LoanField.TENURE, KeyboardType.Number) {
                    onEvent(AddLoanEvent.Tenure(it))
                }
            }
            FilterChip(
                selected = state.tenureInYears,
                onClick = { onEvent(AddLoanEvent.TenureUnit(true)) },
                label = { Text(stringResource(R.string.add_loan_years)) },
            )
            FilterChip(
                selected = !state.tenureInYears,
                onClick = { onEvent(AddLoanEvent.TenureUnit(false)) },
                label = { Text(stringResource(R.string.add_loan_months)) },
            )
        }
        Field(state.emi, R.string.add_loan_emi, state.error == LoanField.EMI, KeyboardType.Decimal) {
            onEvent(AddLoanEvent.Emi(it))
        }
        state.formulaEmi?.let {
            Text(
                stringResource(R.string.add_loan_emi_hint, it.formatted()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.add_loan_first_emi, state.firstEmiDate.shortText()),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { pickingDate = true }) { Text(stringResource(R.string.add_loan_change)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.add_loan_only_me), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.add_loan_only_me_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.onlyMe, onCheckedChange = { onEvent(AddLoanEvent.OnlyMe(it)) })
        }
        ErrorText(state.error)
        Button(
            onClick = { onEvent(AddLoanEvent.Save) },
            enabled = !state.saving,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
        ) {
            Text(stringResource(R.string.add_loan_save))
        }
    }

    if (pickingDate) {
        FirstEmiDialog(
            initial = state.firstEmiDate,
            onPick = {
                onEvent(AddLoanEvent.FirstEmi(it))
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

@Composable
private fun Field(
    value: String,
    label: Int,
    isError: Boolean,
    keyboard: KeyboardType,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorText(error: LoanField?) {
    val text =
        when (error) {
            null -> return
            LoanField.LENDER -> R.string.error_lender
            LoanField.AMOUNT -> R.string.error_loan_amount
            LoanField.RATE -> R.string.error_rate
            LoanField.TENURE -> R.string.error_tenure
            LoanField.EMI -> R.string.error_emi
            LoanField.SAVE -> R.string.error_save_loan
        }
    Text(stringResource(text), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FirstEmiDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // DatePicker works in UTC millis. Past dates are fine: many loans started years ago.
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}
