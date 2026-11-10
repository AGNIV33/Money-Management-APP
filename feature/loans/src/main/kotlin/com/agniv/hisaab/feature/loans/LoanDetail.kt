package com.agniv.hisaab.feature.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.loan.EmiMath
import com.agniv.hisaab.core.domain.loan.Installment
import com.agniv.hisaab.core.domain.loan.LoanForm
import com.agniv.hisaab.core.domain.loan.LoanProgress
import com.agniv.hisaab.core.domain.usecase.LoanItem
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun LoanDetail(
    item: LoanItem,
    today: LocalDate,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetPaid: (YearMonth, Boolean) -> Unit,
) {
    val loan = item.loan
    val progress = item.progress
    // Up to 480 rows of maths; only redo it when the loan itself changes.
    val schedule = remember(loan) { if (EmiMath.isRepayable(loan.terms)) EmiMath.schedule(loan.terms) else emptyList() }
    val totalInterest = remember(schedule) { schedule.map { it.interest }.sum() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // The newest month marked in the app; undo un-marks it (EMIs before the add date have no mark).
    val lastMarked = item.paidMonths.maxOrNull()

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item(key = "top") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("◀ " + stringResource(R.string.loan_back)) }
                Text("", Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text(stringResource(R.string.loan_edit)) }
                TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.loan_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item(key = "head") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(loan.type.emoji(), style = MaterialTheme.typography.headlineMedium)
                Column(Modifier.padding(start = Spacing.m)) {
                    Text(loan.lender, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(loan.type.labelRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "next") { NextEmiCard(item, onSetPaid, lastMarked) }
        item(key = "facts") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    PaidBar(progress.paidFraction)
                    Text(stringResource(R.string.loan_paid_of, progress.paidCount, progress.tenureMonths))
                    Fact(R.string.loan_left, progress.outstanding.formatted())
                    Fact(R.string.loan_principal, loan.principal.formatted())
                    Fact(R.string.loan_rate, stringResource(R.string.loan_rate_value, LoanForm.rateText(loan.annualRateBps)))
                    Fact(R.string.loan_tenure, stringResource(R.string.loan_months_value, loan.tenureMonths))
                    if (schedule.isNotEmpty()) Fact(R.string.loan_total_interest, totalInterest.formatted())
                }
            }
        }
        if (schedule.isNotEmpty()) {
            item(key = "schedule-title") {
                Text(stringResource(R.string.loan_schedule), style = MaterialTheme.typography.titleMedium)
            }
            item(key = "schedule-head") {
                ScheduleRow(
                    no = stringResource(R.string.loan_schedule_no),
                    date = stringResource(R.string.loan_schedule_date),
                    interest = stringResource(R.string.loan_schedule_interest),
                    balance = stringResource(R.string.loan_schedule_balance),
                    header = true,
                )
            }
            items(schedule, key = { "row-${it.number}" }) { row ->
                InstallmentRow(row, paid = row.number <= progress.paidCount, item = item, today = today)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.loan_delete_title)) },
            text = { Text(stringResource(R.string.loan_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.loan_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun NextEmiCard(
    item: LoanItem,
    onSetPaid: (YearMonth, Boolean) -> Unit,
    lastMarked: YearMonth?,
) {
    val next = item.progress.next
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (next == null) {
                Text(
                    stringResource(R.string.loans_closed),
                    style = MaterialTheme.typography.titleMedium,
                    color = HisaabTheme.extendedColors.success,
                )
            } else {
                Text(stringResource(R.string.loan_next_emi), style = MaterialTheme.typography.titleMedium)
                Text(item.loan.emi.formatted(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.tertiary)
                Text(next.date.shortText() + " • " + dueText(next), color = dueColor(next.status))
                Button(onClick = { onSetPaid(next.month, true) }, modifier = Modifier.fillMaxWidth()) {
                    Text("✓ " + stringResource(R.string.mark_paid))
                }
            }
            if (lastMarked != null) {
                OutlinedButton(onClick = { onSetPaid(lastMarked, false) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.undo_paid))
                }
            }
        }
    }
}

@Composable
private fun Fact(
    label: Int,
    value: String,
) {
    Row {
        Text(
            stringResource(label),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun InstallmentRow(
    row: Installment,
    paid: Boolean,
    item: LoanItem,
    today: LocalDate,
) {
    val date = LoanProgress.dueDate(item.loan, row.number)
    val overdue = !paid && date < today
    Column {
        ScheduleRow(
            no = if (paid) "✅" else row.number.toString(),
            date = date.shortText(),
            interest = row.interest.formatted(),
            balance = row.balance.formatted(),
            header = false,
            warn = overdue,
        )
        HorizontalDivider()
    }
}

@Composable
private fun ScheduleRow(
    no: String,
    date: String,
    interest: String,
    balance: String,
    header: Boolean,
    warn: Boolean = false,
) {
    val style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
    val color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(Modifier.padding(vertical = Spacing.xs)) {
        Text(no, style = style, color = color, modifier = Modifier.weight(NO_WEIGHT))
        Text(date, style = style, color = color, modifier = Modifier.weight(DATE_WEIGHT))
        Text(interest, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(MONEY_WEIGHT))
        Text(balance, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(MONEY_WEIGHT))
    }
}

private const val NO_WEIGHT = 0.5f
private const val DATE_WEIGHT = 1.2f
private const val MONEY_WEIGHT = 1f
