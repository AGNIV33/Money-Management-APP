package com.agniv.hisaab.feature.expenses.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.EmptyState
import com.agniv.hisaab.core.designsystem.component.HAmountText
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.HProgressBar
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.component.progressPercent
import com.agniv.hisaab.core.designsystem.theme.DisplayAmount
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.usecase.BudgetLevel
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.home.ExpensesHomeViewModel
import com.agniv.hisaab.feature.expenses.home.HomeEvent
import com.agniv.hisaab.feature.expenses.home.dayLabel
import com.agniv.hisaab.feature.expenses.ui.CategoryTile
import com.agniv.hisaab.feature.expenses.ui.label
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Budget category detail (new; PDF screen 10): how much of one category's limit is used this month, in words as well
 * as colour, and every expense in it. Tapping an expense opens it for editing.
 */
@Composable
fun BudgetCategoryScreen(
    categoryId: String,
    month: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: ExpensesHomeViewModel = hiltViewModel(),
) {
    LaunchedEffect(categoryId, month) {
        viewModel.onEvent(HomeEvent.SetMonth(YearMonth.parse(month)))
        viewModel.onEvent(HomeEvent.FilterCategory(categoryId))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val category = state.categories[categoryId]
    val budget = state.categoryBudgets.firstOrNull { it.categoryId == categoryId }
    val expenses = state.days.flatMap { it.expenses }
    val spent = budget?.spent ?: state.filteredTotal
    val locale = LocalConfiguration.current.locales[0]

    Scaffold(
        topBar = {
            KinlyTopBar(
                title = category?.let { stringResource(R.string.budget_detail_title, it.label()) }.orEmpty(),
                onBack = onBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            item(key = "head") {
                HCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .padding(Spacing.l)
                            .semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Text(
                            state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            HAmountText(spent, style = DisplayAmount, countUp = true)
                            Text(
                                " " + stringResource(R.string.budget_detail_spent),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        if (budget != null) {
                            Text(
                                stringResource(R.string.budget_detail_of, budget.limit.formatted()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HProgressBar(budget.fraction, height = 10.dp)
                            val left = budget.limit - budget.spent
                            Text(
                                when (budget.level) {
                                    BudgetLevel.OVER -> stringResource(R.string.cat_limit_over, "▲", (-left).formatted())
                                    else -> stringResource(R.string.budget_detail_percent, progressPercent(budget.fraction))
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color =
                                    when (budget.level) {
                                        BudgetLevel.OVER -> MaterialTheme.colorScheme.error
                                        BudgetLevel.WARNING -> HisaabTheme.extendedColors.warning
                                        BudgetLevel.OK -> MaterialTheme.colorScheme.primary
                                    },
                            )
                        } else {
                            Text(
                                stringResource(R.string.budget_detail_no_limit),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item(key = "title") { Text(stringResource(R.string.budget_detail_recent), style = MaterialTheme.typography.titleMedium) }
            if (!state.loading && expenses.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.ReceiptLong,
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.budget_detail_empty),
                    )
                }
            } else {
                item(key = "list") {
                    HCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = Spacing.s)) {
                            expenses.forEachIndexed { i, expense ->
                                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.l))
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onEdit(expense.id) }
                                        .heightIn(min = 64.dp)
                                        .padding(horizontal = Spacing.l, vertical = Spacing.s),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                                ) {
                                    CategoryTile(category)
                                    Column(Modifier.weight(1f)) {
                                        Text(expense.note ?: category?.label().orEmpty(), style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            dayLabel(expense.date),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    HAmountText(expense.amount, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
