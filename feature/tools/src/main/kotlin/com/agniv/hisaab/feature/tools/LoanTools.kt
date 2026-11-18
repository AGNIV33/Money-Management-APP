package com.agniv.hisaab.feature.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
        amount.value = AmountInput.cleanAmount(LoanForm.amountText(loan.principal))
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

/** "Fill from my loans" chips, then amount, rate and tenure. */
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
    MoneyField(inputs.amount, R.string.field_amount)
    PercentField(inputs.rate, R.string.field_rate)
    TenureInput(inputs)
}

/** PRD F9b: EMI, total interest, principal vs interest donut, year-by-year table. */
@Composable
internal fun EmiTool(
    state: ToolsUiState,
    onResult: (RecentResult?) -> Unit,
) {
    val inputs = rememberLoanInputs()
    InputCard { LoanInputFields(inputs, state.openLoans) }
    val terms = inputs.terms
    LaunchedEffect(terms) {
        onResult(terms?.let { recent(Tool.EMI, EmiMath.emi(it).paise, it.tenureMonths.toLong(), it.principal.paise) })
    }
    if (terms == null) {
        EmptyResult(stringResource(R.string.tools_need_inputs))
        return
    }
    val schedule = remember(terms) { EmiMath.schedule(terms) }
    val interest = remember(schedule) { schedule.map { it.interest }.sum() }
    val years = remember(schedule) { ToolsLogic.yearly(schedule) }
    val principalColor = MaterialTheme.colorScheme.primary
    val interestColor = HisaabTheme.extendedColors.gold
    ResultHero(stringResource(R.string.emi_result), EmiMath.emi(terms).formatted())
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            HDonut(
                slices = listOf(DonutSlice(terms.principal.paise, principalColor), DonutSlice(interest.paise, interestColor)),
                description = stringResource(R.string.emi_pie_desc, terms.principal.formatted(), interest.formatted()),
                size = 104.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Stat(stringResource(R.string.emi_principal), terms.principal.formatted(), dot = principalColor)
                Stat(stringResource(R.string.emi_interest), interest.formatted(), dot = interestColor)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Stat(stringResource(R.string.emi_total_interest), interest.formatted())
        Stat(stringResource(R.string.emi_total_payable), (terms.principal + interest).formatted())
    }
    YearTable(years)
}

/** Collapsed by default: a 30-year loan is 30 rows, which would bury the answer. */
@Composable
private fun YearTable(years: List<YearRow>) {
    var open by rememberSaveable { mutableStateOf(false) }
    DetailCard {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.emi_year_table), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(if (open) R.string.tools_hide else R.string.tools_show),
            )
        }
        AnimatedVisibility(open) {
            Column {
                TableRow(
                    stringResource(R.string.col_year),
                    stringResource(R.string.col_principal),
                    stringResource(R.string.col_interest),
                    stringResource(R.string.col_balance),
                    header = true,
                )
                years.forEach { row ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TableRow(row.year.toString(), row.principal.formatted(), row.interest.formatted(), row.balance.formatted())
                }
            }
        }
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
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.padding(vertical = Spacing.s)) {
        Text(a, style = style, color = color, modifier = Modifier.weight(YEAR_WEIGHT))
        Text(b, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(c, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(d, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

private const val YEAR_WEIGHT = 0.5f
