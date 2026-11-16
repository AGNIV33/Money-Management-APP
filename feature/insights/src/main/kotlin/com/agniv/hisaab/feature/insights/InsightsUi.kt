package com.agniv.hisaab.feature.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.GoldCard
import com.agniv.hisaab.core.designsystem.component.HButton
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.AiReportFailure
import com.agniv.hisaab.core.domain.model.AiTip
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.ml.insight.Insight
import com.agniv.hisaab.feature.insights.R
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** DESIGN.md `HInsightCard`: a light-bulb, one plain sentence. */
@Composable
internal fun InsightCard(
    insight: Insight,
    categories: Map<String, Category>,
    modifier: Modifier = Modifier,
) {
    HCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(if (insight.isAlert()) "⚠️" else "💡", style = MaterialTheme.typography.titleLarge)
            Text(insightText(insight, categories), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Needs a look now (in-app alert), rather than a friendly fact. */
internal fun Insight.isAlert(): Boolean =
    when (this) {
        is Insight.BudgetPace -> alreadyOver || crossesOnDay != null
        is Insight.BigExpense, is Insight.CategoryJump -> true
        is Insight.SavingsRate -> saved.paise < 0
        is Insight.EmiLoad -> ofIncome && percent > Insight.HIGH_EMI_PERCENT
        else -> false
    }

/**
 * Home card: the most important insight, tap for all and the month's report card.
 * With no insight yet but some spending, a plain link to the report card instead. Hidden when there is nothing.
 */
@Composable
internal fun HomeInsightCard(
    state: InsightsUiState,
    onOpen: () -> Unit,
) {
    val top = state.insights.firstOrNull()
    if (top == null && state.monthly == null) return
    val gold = HisaabTheme.extendedColors
    // Board "Spending Insight": cream card, gold bulb, one sentence, chevron. Tap opens Insights.
    GoldCard(Modifier.clickable(onClick = onOpen)) {
        Row(
            Modifier
                .padding(Spacing.l)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = gold.gold, modifier = Modifier.size(36.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.insights_home_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = gold.onGoldContainer,
                )
                Text(
                    if (top != null) insightText(top, state.categories) else stringResource(R.string.report_card_open),
                    style = MaterialTheme.typography.bodyMedium,
                    color = gold.onGoldContainer,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = gold.onGoldContainer)
        }
    }
}

@Composable
internal fun InsightsContent(
    state: InsightsUiState,
    report: ReportUiState,
    onGetTips: (String) -> Unit,
    onSetIncome: (Money?) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        KinlyTopBar(title = stringResource(R.string.insights_title))
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            state.monthly?.let { MonthlyReportCard(it, state.categories) }
            state.insights.forEach { InsightCard(it, state.categories) }
            IncomeCard(state.income, onSetIncome)
            ReportSection(report, onGetTips)
            Text(
                stringResource(R.string.insights_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// One sentence per kind of insight, so one branch each.
@Suppress("CyclomaticComplexMethod")
@Composable
private fun insightText(
    insight: Insight,
    categories: Map<String, Category>,
): String {
    @Composable fun name(id: String) = categories[id]?.label() ?: stringResource(R.string.insights_this_category)
    val crossesOnDay = (insight as? Insight.BudgetPace)?.crossesOnDay
    return when (insight) {
        is Insight.MonthChange ->
            if (insight.percent >= 0) {
                stringResource(R.string.insight_month_more, insight.percent, insight.spent.formatted(), insight.lastSamePeriod.formatted())
            } else {
                stringResource(
                    R.string.insight_month_less,
                    abs(insight.percent),
                    insight.spent.formatted(),
                    insight.lastSamePeriod.formatted(),
                )
            }
        is Insight.CategoryJump ->
            stringResource(
                R.string.insight_category_jump,
                name(insight.categoryId),
                insight.percent,
                insight.spent.formatted(),
                insight.lastSamePeriod.formatted(),
            )
        is Insight.BigExpense ->
            stringResource(R.string.insight_big_expense, name(insight.categoryId), insight.amount.formatted(), insight.typical.formatted())
        is Insight.RepeatCandidate ->
            stringResource(R.string.insight_repeat, insight.amount.formatted(), name(insight.categoryId), insight.dayOfMonth)
        is Insight.MonthEndForecast ->
            stringResource(
                R.string.insight_month_end,
                insight.projected.formatted(),
                insight.spentSoFar.formatted(),
            )
        is Insight.NextMonthForecast -> stringResource(R.string.insight_next_month, insight.forecast.formatted(), insight.basedOnMonths)
        is Insight.BudgetPace ->
            when {
                insight.alreadyOver -> stringResource(R.string.insight_budget_over, insight.budget.formatted(), insight.spent.formatted())
                crossesOnDay != null ->
                    stringResource(
                        R.string.insight_budget_cross,
                        insight.budget.formatted(),
                        crossesOnDay,
                        insight.safePerDay.formatted(),
                    )
                else ->
                    stringResource(
                        R.string.insight_budget_ok,
                        (insight.budget - insight.spent).formatted(),
                        insight.safePerDay.formatted(),
                        insight.daysLeft,
                    )
            }
        is Insight.EmiLoad ->
            stringResource(
                if (insight.ofIncome) R.string.insight_emi_income else R.string.insight_emi_load,
                insight.percent,
                insight.emi.formatted(),
                insight.base.formatted(),
            )
        is Insight.SavingsRate -> savingsText(insight)
    }
}

@Composable
private fun savingsText(insight: Insight.SavingsRate): String {
    val spent = insight.spent.formatted()
    val income = insight.income.formatted()
    return when {
        insight.saved.paise < 0 ->
            stringResource(
                if (insight.lastMonth) R.string.insight_savings_minus_last else R.string.insight_savings_minus,
                spent,
                income,
                (-insight.saved).formatted(),
            )
        insight.lastMonth -> stringResource(R.string.insight_savings_last, insight.saved.formatted(), insight.percent, income)
        else -> stringResource(R.string.insight_savings_pace, insight.saved.formatted(), insight.percent, income)
    }
}

/** The AI saving tips for last month: asked for on purpose, with the privacy promise shown first. */
@Composable
private fun ReportSection(
    report: ReportUiState,
    onGetTips: (String) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val monthName = report.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    HorizontalDivider()
    Text(stringResource(R.string.report_title, monthName), style = MaterialTheme.typography.titleMedium)
    when (report.phase) {
        ReportPhase.READY -> report.tips.forEach { TipCard(it) }
        ReportPhase.LOADING -> Text(stringResource(R.string.report_loading), style = MaterialTheme.typography.bodyLarge)
        else -> {
            Text(
                stringResource(R.string.report_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            report.failure?.let {
                val res =
                    when (it) {
                        AiReportFailure.NOT_CONFIGURED -> R.string.report_err_not_ready
                        AiReportFailure.OFFLINE -> R.string.report_err_offline
                        AiReportFailure.NOT_ALLOWED -> R.string.report_err_signin
                        AiReportFailure.FAILED -> R.string.report_err_failed
                    }
                Text(stringResource(res), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            HButton(onClick = { onGetTips(locale.language) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.report_get))
            }
        }
    }
}

@Composable
private fun TipCard(tip: AiTip) {
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("✨ " + tip.title, style = MaterialTheme.typography.titleMedium)
            Text(tip.detail, style = MaterialTheme.typography.bodyMedium)
            if (tip.estSaving.paise > 0) {
                Text(
                    stringResource(R.string.report_saving, tip.estSaving.formatted()),
                    style = MaterialTheme.typography.labelLarge,
                    color = HisaabTheme.extendedColors.success,
                )
            }
            Text(
                stringResource(R.string.report_ai_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
