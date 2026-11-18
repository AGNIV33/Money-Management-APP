package com.agniv.hisaab.feature.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
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
internal fun PrepayTool(
    state: ToolsUiState,
    onResult: (RecentResult?) -> Unit,
) {
    val inputs = rememberLoanInputs()
    val paid = rememberSaveable { mutableStateOf("0") }
    val extra = rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(PrepayMode.REDUCE_TENURE) }
    InputCard {
        LoanInputFields(inputs, state.openLoans, onFill = { item ->
            inputs.fillFrom(item)
            paid.value = item.progress.paidCount.toString()
        })
        Stepper(paid, R.string.prepay_paid)
        MoneyField(extra, R.string.prepay_extra)
        Segmented(
            options = listOf(R.string.prepay_shorter, R.string.prepay_smaller),
            selected = if (mode == PrepayMode.REDUCE_TENURE) 0 else 1,
            onSelect = { mode = if (it == 0) PrepayMode.REDUCE_TENURE else PrepayMode.REDUCE_EMI },
        )
    }
    val terms = inputs.terms
    val paidCount = paid.value.trim().toIntOrNull()
    val extraMoney = LoanForm.parseAmount(extra.value)
    val result = remember(terms, paidCount, extraMoney, mode) { ToolsLogic.prepayOrNull(terms, paidCount, extraMoney, mode) }
    LaunchedEffect(result) {
        onResult(result?.let { recent(Tool.PREPAY, extraMoney?.paise ?: 0, it.interestSaved.paise, it.monthsSaved.toLong()) })
    }
    when {
        terms == null || extraMoney == null -> EmptyResult(stringResource(R.string.prepay_need_inputs), Icons.Outlined.Savings)
        paidCount == null || paidCount !in 0 until terms.tenureMonths -> EmptyResult(stringResource(R.string.prepay_bad_paid))
        result == null ->
            EmptyResult(stringResource(R.string.prepay_bad_extra, EmiMath.balanceAfter(terms, paidCount).formatted()))
        else -> {
            ResultHero(stringResource(R.string.prepay_saved), result.interestSaved.formatted()) {
                Text(
                    if (mode == PrepayMode.REDUCE_TENURE) {
                        stringResource(R.string.prepay_months_saved, result.monthsSaved)
                    } else {
                        stringResource(R.string.prepay_new_emi, result.emiAfter.formatted(), result.emiBefore.formatted())
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            DetailCard {
                Text(stringResource(R.string.prepay_interest_left), style = MaterialTheme.typography.titleMedium)
                val before = stringResource(R.string.prepay_before)
                val after = stringResource(R.string.prepay_after)
                CompareBars(
                    before = before to result.interestLeftBefore,
                    after = after to result.interestLeftAfter,
                    description =
                        stringResource(
                            R.string.prepay_bars_desc,
                            result.interestLeftBefore.formatted(),
                            result.interestLeftAfter.formatted(),
                        ),
                )
                if (mode == PrepayMode.REDUCE_TENURE) {
                    Stat(stringResource(R.string.prepay_months_left), "${result.monthsLeftBefore} → ${result.monthsLeftAfter}")
                } else {
                    Stat(stringResource(R.string.prepay_emi_change), "${result.emiBefore.formatted()} → ${result.emiAfter.formatted()}")
                }
            }
        }
    }
}

/** A number with − and + buttons, so "EMIs already paid" is quick to nudge. */
@Composable
private fun Stepper(
    state: MutableState<String>,
    label: Int,
) {
    val now = state.value.toIntOrNull() ?: 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        CountField(state, label, Modifier.weight(1f))
        IconButton(onClick = { state.value = (now - 1).coerceAtLeast(0).toString() }) {
            Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = stringResource(R.string.tools_less))
        }
        IconButton(onClick = { state.value = (now + 1).toString() }) {
            Icon(Icons.Outlined.AddCircleOutline, contentDescription = stringResource(R.string.tools_more))
        }
    }
}

/** PRD F9d: burden % with a colour gauge and words, plus the biggest loan that stays under 40%. */
@Composable
internal fun AffordTool(
    state: ToolsUiState,
    onResult: (RecentResult?) -> Unit,
) {
    val income = rememberSaveable { mutableStateOf("") }
    val existing = rememberSaveable { mutableStateOf("") }
    // The EMIs already in Kinly are a good starting point; the user can change it.
    LaunchedEffect(state.monthlyEmi) {
        if (existing.value.isEmpty() && !state.monthlyEmi.isZero) {
            existing.value = AmountInput.cleanAmount(LoanForm.amountText(state.monthlyEmi))
        }
    }
    val inputs = rememberLoanInputs()
    InputCard {
        MoneyField(income, R.string.afford_income)
        MoneyField(existing, R.string.afford_existing)
        Text(stringResource(R.string.afford_new_loan), style = MaterialTheme.typography.labelLarge)
        LoanInputFields(inputs, emptyList())
    }
    val incomeMoney = LoanForm.parseAmount(income.value)
    val existingMoney = if (existing.value.isBlank()) Money.ZERO else Money.parse(existing.value)?.takeIf { !it.isNegative }
    val check = ToolsLogic.afford(incomeMoney, existingMoney, inputs.terms)
    LaunchedEffect(check) {
        onResult(check?.let { recent(Tool.AFFORD, it.result.newEmi.paise, Math.round(it.result.burdenPercent * TENTHS)) })
    }
    if (check == null) {
        EmptyResult(stringResource(R.string.afford_need_inputs), Icons.Outlined.Balance)
    } else {
        AffordResult(check)
    }
}

@Composable
private fun AffordResult(check: AffordCheck) {
    val result = check.result
    val (label, advice, color) =
        when (result.level) {
            BurdenLevel.COMFORTABLE -> Triple(R.string.afford_ok, R.string.afford_ok_advice, HisaabTheme.extendedColors.success)
            BurdenLevel.CAUTION -> Triple(R.string.afford_caution, R.string.afford_caution_advice, HisaabTheme.extendedColors.warning)
            BurdenLevel.RISKY -> Triple(R.string.afford_risky, R.string.afford_risky_advice, MaterialTheme.colorScheme.error)
        }
    val percent = String.format(Locale.US, "%.1f", result.burdenPercent)
    DetailCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BurdenGauge(result.burdenPercent, color, stringResource(R.string.afford_burden, percent))
            Text("$percent%", style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.afford_of_income), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(label),
                style = MaterialTheme.typography.titleLarge,
                color = color,
                modifier = Modifier.padding(top = Spacing.s),
            )
            Text(stringResource(advice), style = MaterialTheme.typography.bodyMedium)
        }
    }
    DetailCard {
        Stat(stringResource(R.string.afford_new_emi), result.newEmi.formatted())
        Stat(stringResource(R.string.afford_total_emi), result.totalEmiAfter.formatted())
        val max = check.maxLoan
        Text(
            if (max == null) stringResource(R.string.afford_no_room) else stringResource(R.string.afford_max, max.formatted()),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.afford_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val TENTHS = 10

/** Half-circle gauge filled up to the burden %, capped at 100%. The text next to it carries the meaning for TalkBack. */
@Composable
private fun BurdenGauge(
    percent: Double,
    color: Color,
    description: String,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(
        Modifier
            .width(GAUGE_WIDTH)
            .height(GAUGE_WIDTH / 2 + GAUGE_STROKE / 2)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Canvas(Modifier.width(GAUGE_WIDTH).height(GAUGE_WIDTH)) {
            val stroke = GAUGE_STROKE.toPx()
            val arc = Size(size.width - stroke, size.width - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(track, HALF_TURN, HALF_TURN, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            val sweep = (percent / FULL_PERCENT).coerceIn(0.0, 1.0).toFloat() * HALF_TURN
            drawArc(color, HALF_TURN, sweep, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

private val GAUGE_WIDTH = 200.dp
private val GAUGE_STROKE = 18.dp
private const val HALF_TURN = 180f
private const val FULL_PERCENT = 100.0
