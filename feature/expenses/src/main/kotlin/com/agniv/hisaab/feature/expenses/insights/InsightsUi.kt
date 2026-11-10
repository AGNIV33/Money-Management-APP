package com.agniv.hisaab.feature.expenses.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.ml.insight.Insight
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.ui.label
import kotlin.math.abs

/** DESIGN.md `HInsightCard`: a light-bulb, one plain sentence. */
@Composable
internal fun InsightCard(
    insight: Insight,
    categories: Map<String, Category>,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text("💡", style = MaterialTheme.typography.titleLarge)
            Text(insightText(insight, categories), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Home card: the most important insight, tap for all. Hidden when there is nothing to say. */
@Composable
internal fun HomeInsightCard(
    state: InsightsUiState,
    onOpen: () -> Unit,
) {
    val top = state.insights.firstOrNull() ?: return
    Column(Modifier.clickable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        InsightCard(top, state.categories)
        if (state.insights.size > 1) {
            Text(
                stringResource(R.string.insights_see_all, state.insights.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.s),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InsightsSheet(
    state: InsightsUiState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(R.string.insights_title), style = MaterialTheme.typography.headlineSmall)
            state.insights.forEach { InsightCard(it, state.categories) }
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
                R.string.insight_emi_load,
                insight.percent,
                insight.emi.formatted(),
                insight.averageMonthly.formatted(),
            )
    }
}
