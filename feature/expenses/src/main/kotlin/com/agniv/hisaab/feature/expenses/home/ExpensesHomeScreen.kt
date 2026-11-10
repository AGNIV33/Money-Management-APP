package com.agniv.hisaab.feature.expenses.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.add.AddExpenseSheet
import com.agniv.hisaab.feature.expenses.add.labelRes
import com.agniv.hisaab.feature.expenses.ui.emoji
import com.agniv.hisaab.feature.expenses.ui.label
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Phase 1 home: month total and the expense list. The full dashboard (task 1.11) replaces the header later. */
@Composable
fun ExpensesHomeScreen(
    title: String,
    headerAction: @Composable () -> Unit = {},
    viewModel: ExpensesHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // null = closed, "" = adding, otherwise the id being edited.
    var sheetFor by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Android 13+ needs the user's OK before budget alerts can show; asked right after a budget is set.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val deletedText = stringResource(R.string.deleted)
    val undoText = stringResource(R.string.undo)

    val onDelete: (Expense) -> Unit = { expense ->
        viewModel.onEvent(HomeEvent.Delete(expense.id))
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(deletedText, undoText, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.onEvent(HomeEvent.Undo(expense.id))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { sheetFor = "" },
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    headerAction()
                }
            }
            item(key = "month") { MonthSwitcher(state, viewModel::onEvent) }
            item(key = "summary") { Summary(state) }
            if (state.isCurrentMonth && !state.filter.isActive) {
                item(key = "budget") {
                    BudgetCard(state) { budget ->
                        viewModel.onEvent(HomeEvent.SetBudget(budget))
                        if (budget != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }
            nextEmiItem(state, viewModel::onEvent)
            if (!state.filter.isActive) {
                item(key = "top-categories") { TopCategoriesCard(state) }
                item(key = "who-spent") { WhoSpentCard(state) }
            }
            item(key = "filters") { Filters(state, viewModel::onEvent) }
            if (!state.loading && state.days.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(if (state.hasAnyThisMonth) R.string.no_match else R.string.home_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.xl),
                    )
                }
            }
            state.days.forEach { day ->
                item(key = "day-${day.date}") { DayHeader(day) }
                items(day.expenses, key = { it.id }) { expense ->
                    SwipeToDelete(enabled = state.canDelete(expense), onDelete = { onDelete(expense) }) {
                        ExpenseRow(
                            expense = expense,
                            category = state.categories[expense.categoryId],
                            onClick = { sheetFor = expense.id },
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    sheetFor?.let { id -> AddExpenseSheet(onDismiss = { sheetFor = null }, editId = id.ifEmpty { null }) }
}

private fun LazyListScope.nextEmiItem(
    state: ExpensesHomeUiState,
    onEvent: (HomeEvent) -> Unit,
) {
    val next = state.nextEmi ?: return
    if (!state.isCurrentMonth || state.filter.isActive) return
    item(key = "next-emi") { NextEmiCard(next) { onEvent(HomeEvent.MarkEmiPaid(next.loan.id)) } }
}

@Composable
private fun MonthSwitcher(
    state: ExpensesHomeUiState,
    onEvent: (HomeEvent) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val label = state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    Row(verticalAlignment = Alignment.CenterVertically) {
        val prev = stringResource(R.string.month_previous)
        IconButton(onClick = { onEvent(HomeEvent.PreviousMonth) }, modifier = Modifier.semantics { contentDescription = prev }) {
            Text("◀", style = MaterialTheme.typography.titleMedium)
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
        if (!state.isCurrentMonth) {
            val next = stringResource(R.string.month_next)
            IconButton(onClick = { onEvent(HomeEvent.NextMonth) }, modifier = Modifier.semantics { contentDescription = next }) {
                Text("▶", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Summary(state: ExpensesHomeUiState) {
    HSummaryCard(
        title = stringResource(if (state.isCurrentMonth) R.string.home_month_spent else R.string.home_month_spent_past),
        amount = state.summary?.total ?: Money.ZERO,
        subtitle = summarySubtitle(state),
        subtitleStatus = summaryStatus(state),
    )
}

@Composable
private fun summarySubtitle(state: ExpensesHomeUiState): String? {
    val summary = state.summary ?: return null
    val change = summary.changeFromPrevious
    return when {
        state.filter.isActive -> stringResource(R.string.filtered_total, state.filteredTotal.formatted())
        summary.previousTotal.isZero -> null
        change.isZero -> stringResource(R.string.home_same_as_last)
        change.isNegative -> stringResource(R.string.home_less_than_last, change.abs().formatted())
        else -> stringResource(R.string.home_more_than_last, change.formatted())
    }
}

private fun summaryStatus(state: ExpensesHomeUiState): AmountStatus {
    val change = state.summary?.changeFromPrevious
    return when {
        state.filter.isActive || change == null || change.isZero -> AmountStatus.NEUTRAL
        change.isNegative -> AmountStatus.SUCCESS
        else -> AmountStatus.WARNING
    }
}

@Composable
private fun Filters(
    state: ExpensesHomeUiState,
    onEvent: (HomeEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(top = Spacing.s)) {
        OutlinedTextField(
            value = state.filter.query,
            onValueChange = { onEvent(HomeEvent.Search(it)) },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (state.filter.isActive) {
                item(key = "clear") {
                    FilterChip(
                        selected = false,
                        onClick = { onEvent(HomeEvent.ClearFilters) },
                        label = { Text(stringResource(R.string.clear_filters)) },
                    )
                }
            }
            items(PayMode.entries, key = { it.name }) { mode ->
                val selected = state.filter.mode == mode
                FilterChip(
                    selected = selected,
                    onClick = { onEvent(HomeEvent.FilterMode(if (selected) null else mode)) },
                    label = { Text(stringResource(mode.labelRes())) },
                )
            }
            items(state.categories.values.toList(), key = { it.id }) { category ->
                val selected = state.filter.categoryId == category.id
                FilterChip(
                    selected = selected,
                    onClick = { onEvent(HomeEvent.FilterCategory(if (selected) null else category.id)) },
                    label = { Text("${category.emoji()} ${category.label()}") },
                )
            }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
private fun DayHeader(day: DayGroup) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.m),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            day.date.format(dayFormat),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HAmountText(amount = day.total, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SwipeToDelete(
    enabled: Boolean,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            // Snap back: the row leaves because the list updates, and Undo can bring it back.
            scope.launch { state.reset() }
        },
        backgroundContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = Spacing.l),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    category: Category?,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
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
