package com.agniv.hisaab.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.loan.LoanForm
import com.agniv.hisaab.core.domain.usecase.LoanItem
import java.util.Locale

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

internal const val LOAN_MIN_RUPEES = 500_000f
internal const val LOAN_MAX_RUPEES = 10_000_000f
private const val LOAN_STEP = 10_000f
private const val RATE_MIN = 5f
private const val RATE_MAX = 15f
private const val RATE_STEP = 0.05f
private const val YEARS_MAX = 30
private const val MONTHS_MAX = 360

/** "Fill from my loans" chips, shown above the input card when the family has open loans. */
@Composable
internal fun FillFromLoans(
    loans: List<LoanItem>,
    onFill: (LoanItem) -> Unit,
) {
    if (loans.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.fill_from_loan), style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            items(loans, key = { it.loan.id }) { item ->
                AssistChip(onClick = { onFill(item) }, label = { Text(item.loan.lender) })
            }
        }
    }
}

/** Loan amount, interest rate and tenure rows with sliders (EMI board). */
@Composable
internal fun LoanRows(
    inputs: LoanInputs,
    amountLabel: Int = R.string.field_amount,
    amountHelper: Int = R.string.field_amount_help,
    amountMax: Float = LOAN_MAX_RUPEES,
) {
    AmountRow(inputs.amount, amountLabel, amountHelper, Icons.Outlined.Payments, LOAN_MIN_RUPEES, amountMax, LOAN_STEP)
    RowDivider()
    RateRow(inputs.rate)
    RowDivider()
    TenureRow(inputs)
}

/** ₹ row with a slider; typing can go past the slider's ends. */
@Composable
internal fun AmountRow(
    state: MutableState<String>,
    label: Int,
    helper: Int,
    icon: ImageVector,
    min: Float,
    max: Float,
    step: Float,
) {
    val name = stringResource(label)
    InputRow(
        icon = icon,
        label = label,
        helper = helper,
        value = { ValueBox(state, name, prefix = "₹", grouped = true) },
        slider = { ValueSlider(state, SliderRange(min, max, step, rupeesLabel(min), rupeesLabel(max))) },
    )
}

@Composable
internal fun RateRow(
    rate: MutableState<String>,
    label: Int = R.string.field_rate,
    min: Float = RATE_MIN,
) {
    val name = stringResource(label)
    InputRow(
        icon = Icons.Outlined.Percent,
        label = label,
        helper = R.string.field_rate_help,
        value = { ValueBox(rate, name, suffix = "%") },
        slider = {
            ValueSlider(rate, SliderRange(min, RATE_MAX, RATE_STEP, percentLabel(min), percentLabel(RATE_MAX)), decimals = true)
        },
    )
}

@Composable
internal fun TenureRow(inputs: LoanInputs) {
    val years = inputs.inYears.value
    val max = if (years) YEARS_MAX else MONTHS_MAX
    val name = stringResource(R.string.field_tenure)
    InputRow(
        icon = Icons.Outlined.CalendarMonth,
        label = R.string.field_tenure,
        helper = R.string.field_tenure_help,
        value = {
            ValueBox(
                inputs.tenure,
                name,
                suffix = stringResource(if (years) R.string.unit_years else R.string.unit_months),
                decimal = false,
                width = TENURE_BOX,
            )
        },
        extra = {
            Segmented(
                options = listOf(R.string.unit_years, R.string.unit_months),
                selected = if (years) 0 else 1,
                onSelect = { inputs.inYears.value = it == 0 },
            )
        },
        slider = {
            ValueSlider(inputs.tenure, SliderRange(1f, max.toFloat(), 1f, tenureLabel(1, years), tenureLabel(max, years)))
        },
    )
}

private val TENURE_BOX = 130.dp

@Composable
private fun tenureLabel(
    n: Int,
    years: Boolean,
) = if (years) stringResource(R.string.tenure_years, n) else stringResource(R.string.tenure_months, n)

internal fun rupeesLabel(rupees: Float): String = AmountInput.compact(Money.ofRupees(rupees.toLong()))

internal fun percentLabel(value: Float): String = String.format(Locale.US, "%.0f%%", value)
