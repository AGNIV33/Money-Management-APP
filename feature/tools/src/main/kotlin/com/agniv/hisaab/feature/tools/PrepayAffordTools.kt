package com.agniv.hisaab.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.loan.BurdenLevel
import com.agniv.hisaab.core.domain.loan.EmiMath
import com.agniv.hisaab.core.domain.loan.LoanForm
import com.agniv.hisaab.core.domain.loan.PrepayMode
import java.util.Locale

/** PRD F9c: effect of a lump sum now, either finishing sooner or paying a smaller EMI. */
@Composable
internal fun PrepayTool(state: ToolsUiState) {
    val inputs = rememberLoanInputs()
    val paid = rememberSaveable { mutableStateOf("0") }
    val extra = rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(PrepayMode.REDUCE_TENURE) }
    LoanInputFields(inputs, state.openLoans, onFill = { item ->
        inputs.fillFrom(item)
        paid.value = item.progress.paidCount.toString()
    })
    NumberField(paid, R.string.prepay_paid, KeyboardType.Number)
    NumberField(extra, R.string.prepay_extra)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        FilterChip(
            selected = mode == PrepayMode.REDUCE_TENURE,
            onClick = { mode = PrepayMode.REDUCE_TENURE },
            label = { Text(stringResource(R.string.prepay_shorter)) },
        )
        FilterChip(
            selected = mode == PrepayMode.REDUCE_EMI,
            onClick = { mode = PrepayMode.REDUCE_EMI },
            label = { Text(stringResource(R.string.prepay_smaller)) },
        )
    }
    val terms = inputs.terms
    val paidCount = paid.value.trim().toIntOrNull()
    val extraMoney = LoanForm.parseAmount(extra.value)
    when {
        terms == null || extraMoney == null -> Hint(stringResource(R.string.tools_need_inputs))
        paidCount == null || paidCount !in 0 until terms.tenureMonths -> Hint(stringResource(R.string.prepay_bad_paid))
        else -> {
            val balance = EmiMath.balanceAfter(terms, paidCount)
            if (extraMoney >= balance) {
                Hint(stringResource(R.string.prepay_bad_extra, balance.formatted()))
                return
            }
            val result = remember(terms, paidCount, extraMoney, mode) { EmiMath.prepay(terms, paidCount, extraMoney, mode) }
            ResultCard {
                Text(stringResource(R.string.prepay_saved), style = MaterialTheme.typography.titleMedium)
                Text(
                    result.interestSaved.formatted(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = HisaabTheme.extendedColors.success,
                )
                if (mode == PrepayMode.REDUCE_TENURE) {
                    Text(stringResource(R.string.prepay_months_saved, result.monthsSaved))
                } else {
                    Text(stringResource(R.string.prepay_new_emi, result.emiAfter.formatted(), result.emiBefore.formatted()))
                }
            }
        }
    }
}

/** PRD F9d: burden % with words and colour, plus the biggest loan that stays under 40%. */
@Composable
internal fun AffordTool(state: ToolsUiState) {
    val income = rememberSaveable { mutableStateOf("") }
    val existing = rememberSaveable { mutableStateOf("") }
    // The EMIs already in Kinly are a good starting point; the user can change it.
    LaunchedEffect(state.monthlyEmi) {
        if (existing.value.isEmpty() && !state.monthlyEmi.isZero) existing.value = LoanForm.amountText(state.monthlyEmi)
    }
    val inputs = rememberLoanInputs()
    NumberField(income, R.string.afford_income)
    NumberField(existing, R.string.afford_existing)
    LoanInputFields(inputs, emptyList())
    val incomeMoney = LoanForm.parseAmount(income.value)
    val existingMoney = if (existing.value.isBlank()) Money.ZERO else Money.parse(existing.value)?.takeIf { !it.isNegative }
    val terms = inputs.terms
    if (incomeMoney == null || existingMoney == null || terms == null) {
        Hint(stringResource(R.string.tools_need_inputs))
        return
    }
    val newEmi = EmiMath.emi(terms)
    val result = EmiMath.affordability(incomeMoney, existingMoney, newEmi)
    val (label, color) =
        when (result.level) {
            BurdenLevel.COMFORTABLE -> R.string.afford_ok to HisaabTheme.extendedColors.success
            BurdenLevel.CAUTION -> R.string.afford_caution to HisaabTheme.extendedColors.warning
            BurdenLevel.RISKY -> R.string.afford_risky to MaterialTheme.colorScheme.error
        }
    ResultCard {
        Fact(R.string.afford_new_emi, newEmi.formatted())
        Text(
            stringResource(R.string.afford_burden, String.format(Locale.US, "%.1f", result.burdenPercent)),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(label), style = MaterialTheme.typography.headlineSmall, color = color)
        val max = ToolsLogic.maxAffordableLoan(incomeMoney, existingMoney, terms.annualRateBps, terms.tenureMonths)
        Text(if (max == null) stringResource(R.string.afford_no_room) else stringResource(R.string.afford_max, max.formatted()))
        Hint(stringResource(R.string.afford_note))
    }
}
