package com.agniv.hisaab.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.IconTile
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.loan.EmiMath
import com.agniv.hisaab.core.domain.loan.LoanForm
import com.agniv.hisaab.core.domain.loan.LoanTerms
import com.agniv.hisaab.core.domain.loan.PrepayMode
import com.agniv.hisaab.core.domain.loan.PrepaymentResult
import com.agniv.hisaab.core.domain.tools.RecentResult
import java.util.Locale

/** PRD F9c, Prepayment Saver board: effect of a lump sum now, finishing sooner or paying a smaller EMI. */
@Composable
internal fun PrepayTool(
    state: ToolsUiState,
    onResult: (RecentResult?) -> Unit,
) {
    val inputs = rememberLoanInputs()
    val paid = rememberSaveable { mutableStateOf("0") }
    val extra = rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(PrepayMode.REDUCE_TENURE) }
    FillFromLoans(state.openLoans) { item ->
        inputs.fillFrom(item)
        paid.value = item.progress.paidCount.toString()
    }
    val paidName = stringResource(R.string.prepay_paid)
    val extraName = stringResource(R.string.prepay_extra)
    InputCard {
        LoanRows(inputs, amountHelper = R.string.prepay_amount_help)
        RowDivider()
        InputRow(Icons.Outlined.FormatListNumbered, R.string.prepay_paid, R.string.prepay_paid_help, { StepperBox(paid, paidName) })
        RowDivider()
        InputRow(Icons.Outlined.Paid, R.string.prepay_extra, R.string.prepay_extra_help, {
            ValueBox(extra, extraName, prefix = "₹", grouped = true)
        })
    }
    Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        ModeCard(mode == PrepayMode.REDUCE_TENURE, Icons.Outlined.RocketLaunch, R.string.prepay_shorter, R.string.prepay_shorter_help) {
            mode = PrepayMode.REDUCE_TENURE
        }
        ModeCard(mode == PrepayMode.REDUCE_EMI, Icons.Outlined.Toll, R.string.prepay_smaller, R.string.prepay_smaller_help) {
            mode = PrepayMode.REDUCE_EMI
        }
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
        else -> PrepayResultView(terms, paidCount, extraMoney, result)
    }
}

@Composable
private fun RowScope.ModeCard(
    selected: Boolean,
    icon: ImageVector,
    title: Int,
    helper: Int,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else scheme.surface)
            .border(1.dp, if (selected) scheme.primary else scheme.outlineVariant, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        val content = if (selected) scheme.onPrimaryContainer else scheme.onSurface
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = content)
            Text(
                stringResource(helper),
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) content else scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrepayResultView(
    terms: LoanTerms,
    paidCount: Int,
    extra: Money,
    result: PrepaymentResult,
) {
    val totals = remember(terms, paidCount, result) { ToolsLogic.prepayTotals(terms, paidCount, result) }
    val shorter = result.mode == PrepayMode.REDUCE_TENURE
    val sooner = yearsText(result.monthsSaved)
    ResultCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Icon(Icons.Outlined.Savings, contentDescription = null, modifier = Modifier.size(48.dp))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(R.string.prepay_you_save, AmountInput.compact(result.interestSaved)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        if (shorter) {
                            stringResource(R.string.prepay_ends_earlier, sooner)
                        } else {
                            stringResource(R.string.prepay_new_emi, result.emiAfter.formatted(), result.emiBefore.formatted())
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            IconTile(Icons.Outlined.BarChart, size = 36.dp)
            Text(stringResource(R.string.prepay_before_after), style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            LegendDot(MaterialTheme.colorScheme.outline, stringResource(R.string.prepay_before))
            LegendDot(MaterialTheme.colorScheme.primary, stringResource(R.string.prepay_after_with, extra.formatted()))
        }
        RowDivider()
        CompareRow(
            icon = Icons.Outlined.Toll,
            label = stringResource(R.string.emi_total_interest),
            before = totals.interestBefore.paise to AmountInput.compact(totals.interestBefore),
            after = totals.interestAfter.paise to AmountInput.compact(totals.interestAfter),
            delta = AmountInput.compact(result.interestSaved),
            deltaNote = stringResource(R.string.prepay_less, ToolsLogic.share(result.interestSaved, totals.interestBefore)),
        )
        RowDivider()
        if (shorter) {
            CompareRow(
                icon = Icons.Outlined.CalendarMonth,
                label = stringResource(R.string.prepay_tenure),
                before = totals.monthsBefore.toLong() to yearsText(totals.monthsBefore),
                after = totals.monthsAfter.toLong() to yearsText(totals.monthsAfter),
                delta = sooner,
                deltaNote = stringResource(R.string.prepay_earlier),
            )
        } else {
            CompareRow(
                icon = Icons.Outlined.Paid,
                label = stringResource(R.string.prepay_emi_change),
                before = result.emiBefore.paise to result.emiBefore.formatted(),
                after = result.emiAfter.paise to result.emiAfter.formatted(),
                delta = (result.emiBefore - result.emiAfter).formatted(),
                deltaNote = stringResource(R.string.prepay_per_month_less),
            )
        }
        TipBox(Icons.Outlined.Lightbulb) {
            Text(
                if (shorter) {
                    stringResource(R.string.prepay_tip_sooner, extra.formatted(), result.interestSaved.formatted(), sooner)
                } else {
                    stringResource(R.string.prepay_tip_smaller, extra.formatted(), result.interestSaved.formatted())
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun yearsText(months: Int): String =
    stringResource(R.string.tenure_years_decimal, String.format(Locale.US, "%.1f", months / MONTHS_IN_YEAR))

private const val MONTHS_IN_YEAR = 12.0

@Composable
private fun LegendDot(
    color: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Two bars (before grey, after green) with their values, and the saving in a pill on the right. */
@Composable
private fun CompareRow(
    icon: ImageVector,
    label: String,
    before: Pair<Long, String>,
    after: Pair<Long, String>,
    delta: String,
    deltaNote: String,
) {
    val max = maxOf(before.first, after.first).coerceAtLeast(1).toFloat()
    Column(
        Modifier.clearAndSetSemantics { contentDescription = "$label: ${before.second} → ${after.second}, $delta $deltaNote" },
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            IconTile(icon, size = 32.dp)
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            DeltaPill(delta, deltaNote)
        }
        BarLine(before.first / max, MaterialTheme.colorScheme.outline, before.second)
        BarLine(after.first / max, MaterialTheme.colorScheme.primary, after.second)
    }
}

@Composable
private fun BarLine(
    share: Float,
    color: Color,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Box(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(share.coerceIn(MIN_BAR, 1f))
                    .height(BAR_HEIGHT)
                    .clip(BAR_SHAPE)
                    .background(color),
            )
        }
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Spacing.xs))
    }
}

private val BAR_HEIGHT = 14.dp
private val BAR_SHAPE = RoundedCornerShape(7.dp)
private const val MIN_BAR = 0.02f

@Composable
private fun DeltaPill(
    value: String,
    note: String,
) {
    Row(
        Modifier
            .border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            Icons.Outlined.ArrowDownward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
