package com.agniv.hisaab.feature.expenses.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.AmountStatus
import com.agniv.hisaab.core.designsystem.component.HAmountText
import com.agniv.hisaab.core.designsystem.component.HSummaryCard
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.add.AddExpenseSheet
import com.agniv.hisaab.feature.expenses.add.labelRes
import com.agniv.hisaab.feature.expenses.ui.emoji
import com.agniv.hisaab.feature.expenses.ui.label
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Phase 1 home: this month's total and list. The full dashboard (task 1.11) replaces the header later. */
@Composable
fun ExpensesHomeScreen(
    title: String,
    viewModel: ExpensesHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Expense?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                text = { Text(stringResource(R.string.add_expense_fab)) },
                icon = { Text("+", style = MaterialTheme.typography.headlineSmall) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item(key = "title") {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            item(key = "summary") { Summary(state) }
            item(key = "recent") {
                Text(
                    stringResource(R.string.home_this_month),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.m),
                )
            }
            if (!state.loading && state.expenses.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.home_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.xl),
                    )
                }
            }
            items(state.expenses, key = { it.id }) { expense ->
                ExpenseRow(
                    expense = expense,
                    category = state.categories[expense.categoryId],
                    onLongPress = { confirmDelete = expense },
                )
                HorizontalDivider()
            }
        }
    }

    if (adding) AddExpenseSheet(onDismiss = { adding = false })

    confirmDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body, expense.amount.formatted())) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(expense.id)
                    confirmDelete = null
                }) { Text(stringResource(R.string.delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Summary(state: ExpensesHomeUiState) {
    val summary = state.summary
    val change = summary?.changeFromPrevious
    val subtitle =
        when {
            summary == null || summary.previousTotal.isZero -> null
            change == null || change.isZero -> stringResource(R.string.home_same_as_last)
            change.isNegative -> stringResource(R.string.home_less_than_last, change.abs().formatted())
            else -> stringResource(R.string.home_more_than_last, change.formatted())
        }
    HSummaryCard(
        title = stringResource(R.string.home_month_spent),
        amount = summary?.total ?: Money.ZERO,
        subtitle = subtitle,
        subtitleStatus =
            when {
                change == null || change.isZero -> AmountStatus.NEUTRAL
                change.isNegative -> AmountStatus.SUCCESS
                else -> AmountStatus.WARNING
            },
    )
}

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
private fun ExpenseRow(
    expense: Expense,
    category: Category?,
    onLongPress: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(category?.emoji() ?: "📦", style = MaterialTheme.typography.headlineSmall)
        Column(Modifier.weight(1f)) {
            Text(
                text = expense.note ?: category?.label().orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val lock = if (expense.visibility == Visibility.PRIVATE) "🔒 " else ""
            Text(
                text =
                    lock +
                        listOfNotNull(
                            category?.label()?.takeIf { expense.note != null },
                            stringResource(expense.mode.labelRes()),
                            expense.date.format(dateFormat),
                        ).joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HAmountText(amount = expense.amount, style = MaterialTheme.typography.titleMedium)
    }
}
