package com.agniv.hisaab.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.designsystem.component.DonutSlice
import com.agniv.hisaab.core.designsystem.component.HDonut
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.loan.EmiMath
import com.agniv.hisaab.core.domain.loan.LoanForm
import com.agniv.hisaab.core.domain.usecase.LoanItem

/** Amount, rate and tenure, kept across rotation. Shared by the three loan tools. */
internal class LoanInputs(
    val amount: MutableState<String>,
    val rate: MutableState<String>,
    val tenure: MutableState<String>,
    val inYears: MutableState<Boolean>,
) {
    val terms get() = ToolsLogic.terms(amount.value, rate.value, tenure.value, inYears.value)

    fun fillFrom(item: LoanItem) {
        val loan = item.loan
        amount.value = LoanForm.amountText(loan.principal)
        rate.value = LoanForm.rateText(loan.annualRateBps)
        val years = loan.tenureMonths % MONTHS_PER_YEAR == 0
        inYears.value = years
        tenure.value = (if (years) loan.tenureMonths / MONTHS_PER_YEAR else loan.tenureMonths).toString()
    }

    private companion object {
        const val MONTHS_PER_YEAR = 12
    }
}

@Composable
internal fun rememberLoanInputs() =
    LoanInputs(
        rememberSaveable { mutableStateOf("") },
        rememberSaveable { mutableStateOf("") },
        rememberSaveable { mutableStateOf("") },
        rememberSaveable { mutableStateOf(true) },
    )

@Composable
internal fun LoanInputFields(
    inputs: LoanInputs,
    loans: List<LoanItem>,
    onFill: (LoanItem) -> Unit = inputs::fillFrom,
) {
    if (loans.isNotEmpty()) {
        Text(stringResource(R.string.fill_from_loan), style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            items(loans, key = { it.loan.id }) { item ->
                AssistChip(onClick = { onFill(item) }, label = { Text(item.loan.lender) })
            }
        }
    }
    NumberField(inputs.amount, R.string.field_amount)
    NumberField(inputs.rate, R.string.field_rate)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Column(Modifier.weight(1f)) { NumberField(inputs.tenure, R.string.field_tenure, KeyboardType.Number) }
        FilterChip(
            selected = inputs.inYears.value,
            onClick = { inputs.inYears.value = true },
            label = { Text(stringResource(R.string.unit_years)) },
        )
        FilterChip(
            selected = !inputs.inYears.value,
            onClick = { inputs.inYears.value = false },
            label = { Text(stringResource(R.string.unit_months)) },
        )
    }
}

@Composable
internal fun NumberField(
    state: MutableState<String>,
    label: Int,
    keyboard: KeyboardType = KeyboardType.Decimal,
) {
    OutlinedTextField(
        value = state.value,
        onValueChange = { state.value = it },
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun ResultCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) { content() }
    }
}

@Composable
internal fun Fact(
    label: Int,
    value: String,
) {
    Row {
        Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** PRD F9b: EMI, total interest, principal vs interest pie, year-by-year table. */
@Composable
internal fun EmiTool(state: ToolsUiState) {
    val inputs = rememberLoanInputs()
    LoanInputFields(inputs, state.openLoans)
    val terms = inputs.terms
    if (terms == null) {
        Hint(stringResource(R.string.tools_need_inputs))
        return
    }
    val schedule = remember(terms) { EmiMath.schedule(terms) }
    val interest = remember(schedule) { schedule.map { it.interest }.sum() }
    val years = remember(schedule) { ToolsLogic.yearly(schedule) }
    val principalColor = MaterialTheme.colorScheme.tertiary
    val interestColor = HisaabTheme.extendedColors.warning
    ResultCard {
        Text(stringResource(R.string.emi_result), style = MaterialTheme.typography.titleMedium)
        Text(EmiMath.emi(terms).formatted(), style = MaterialTheme.typography.headlineMedium, color = principalColor)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            HDonut(
                slices = listOf(DonutSlice(terms.principal.paise, principalColor), DonutSlice(interest.paise, interestColor)),
                description = stringResource(R.string.emi_pie_desc, terms.principal.formatted(), interest.formatted()),
                size = 96.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Legend(principalColor, R.string.emi_principal, terms.principal)
                Legend(interestColor, R.string.emi_interest, interest)
            }
        }
        Fact(R.string.emi_total_interest, interest.formatted())
        Fact(R.string.emi_total_payable, (terms.principal + interest).formatted())
    }
    Text(stringResource(R.string.emi_year_table), style = MaterialTheme.typography.titleMedium)
    TableRow(
        stringResource(R.string.col_year),
        stringResource(R.string.col_principal),
        stringResource(R.string.col_interest),
        stringResource(R.string.col_balance),
        header = true,
    )
    years.forEach { row ->
        TableRow(row.year.toString(), row.principal.formatted(), row.interest.formatted(), row.balance.formatted())
        HorizontalDivider()
    }
}

@Composable
private fun Legend(
    color: Color,
    label: Int,
    amount: Money,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("●", color = color)
        Text(stringResource(label) + ": " + amount.formatted(), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TableRow(
    a: String,
    b: String,
    c: String,
    d: String,
    header: Boolean = false,
) {
    val style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
    Row(Modifier.padding(vertical = Spacing.xs)) {
        Text(a, style = style, modifier = Modifier.weight(YEAR_WEIGHT))
        Text(b, style = style, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(c, style = style, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(d, style = style, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

private const val YEAR_WEIGHT = 0.5f
