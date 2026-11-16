package com.agniv.hisaab.feature.expenses.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.EmptyState
import com.agniv.hisaab.core.designsystem.component.HAmountText
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.HProgressBar
import com.agniv.hisaab.core.designsystem.component.IconTile
import com.agniv.hisaab.core.designsystem.component.PercentChip
import com.agniv.hisaab.core.designsystem.component.SectionHeader
import com.agniv.hisaab.core.designsystem.component.progressPercent
import com.agniv.hisaab.core.designsystem.theme.DisplayAmount
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.usecase.CategoryBudget
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.add.AddExpenseSheet
import com.agniv.hisaab.feature.expenses.statement.StatementImportSheet
import com.agniv.hisaab.feature.expenses.ui.CategoryTile
import com.agniv.hisaab.feature.expenses.ui.iconVector
import com.agniv.hisaab.feature.expenses.ui.label

/** Where Home's buttons lead; the app owns navigation so features stay apart. */
class HomeActions(
    val onAlerts: () -> Unit,
    val onFamily: () -> Unit,
    val onBudgets: () -> Unit,
    val onAllExpenses: () -> Unit,
    val onEmi: () -> Unit,
    val onUdhaar: () -> Unit,
    val onTools: () -> Unit,
)

/**
 * Home board: greeting, this month's spend against the budget, top category budgets, one spending insight,
 * shortcuts to EMI / Udhaar / import / tools, and the five most recent expenses. Always the current month;
 * other months live behind "View all".
 */
@Composable
fun ExpensesHomeScreen(
    actions: HomeActions,
    // The insight card comes from feature:insights through the app, so the two features stay apart.
    insightCard: @Composable () -> Unit = {},
    viewModel: ExpensesHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // null = closed, "" = adding, otherwise the id being edited.
    var sheetFor by rememberSaveable { mutableStateOf<String?>(null) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    val firstName =
        state.members.values
            .firstOrNull { it.userId == state.viewerUserId }
            ?.name
            ?.substringBefore(' ')

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = { AddFab(onClick = { sheetFor = "" }) },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = FAB_CLEARANCE.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            item(key = "header") { Header(firstName, actions) }
            item(key = "spend") { SpendCard(state) }
            item(key = "budgets") { CategoryBudgetsCard(state, actions.onBudgets) }
            item(key = "insight") { insightCard() }
            item(key = "shortcuts") { Shortcuts(actions, onImport = { showImport = true }) }
            item(key = "recent") { RecentExpenses(state, actions.onAllExpenses, onOpen = { sheetFor = it }) }
        }
    }

    sheetFor?.let { id ->
        AddExpenseSheet(onDismiss = { sheetFor = null }, editId = id.ifEmpty { null })
    }
    if (showImport) StatementImportSheet(onDismiss = { showImport = false })
}

private const val FAB_CLEARANCE = 96
private const val RECENT_COUNT = 5
private const val HOME_BUDGET_ROWS = 3

@Composable
private fun Header(
    firstName: String?,
    actions: HomeActions,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Greeting(firstName, Modifier.weight(1f))
        IconButton(onClick = actions.onAlerts) {
            Icon(Icons.Outlined.NotificationsNone, contentDescription = stringResource(R.string.home_alerts))
        }
        val family = stringResource(R.string.home_family)
        Box(
            Modifier
                .size(48.dp)
                .clickable(onClickLabel = family, role = Role.Button, onClick = actions.onFamily)
                .semantics(mergeDescendants = true) {},
            contentAlignment = Alignment.Center,
        ) {
            IconTile(Icons.Outlined.Groups, size = 44.dp)
        }
    }
}

/** Solid green "This Month's Spend" card; the amount counts up and the bar fills on first show. */
@Composable
private fun SpendCard(state: ExpensesHomeUiState) {
    val colors = HisaabTheme.extendedColors
    val spent = state.summary?.total ?: Money.ZERO
    val budget = state.budget
    HCard(Modifier.fillMaxWidth(), containerColor = colors.hero, contentColor = colors.onHero) {
        Box(Modifier.padding(Spacing.l)) {
            // Decorative "receipt" in the corner, as on the board.
            Icon(
                Icons.Outlined.ReceiptLong,
                contentDescription = null,
                tint = colors.onHero.copy(alpha = RECEIPT_ALPHA),
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(56.dp)
                        .rotate(RECEIPT_TILT),
            )
            Column(
                Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(stringResource(R.string.home_spend_title), style = MaterialTheme.typography.titleSmall, color = colors.onHero)
                HAmountText(amount = spent, style = DisplayAmount, countUp = true, modifier = Modifier.padding(end = 64.dp))
                if (budget != null && !budget.isZero) {
                    val fraction = spent.paise.toFloat() / budget.paise
                    Text(
                        stringResource(R.string.home_of_budget, budget.formatted()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onHero,
                    )
                    Row(
                        Modifier.padding(top = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        // White on the green card at every level; the percent beside it says how full it is.
                        HProgressBar(
                            fraction = fraction,
                            modifier = Modifier.weight(1f),
                            color = colors.onHero,
                            trackColor = colors.onHero.copy(alpha = TRACK_ALPHA),
                        )
                        Text("${progressPercent(fraction)}%", style = MaterialTheme.typography.labelLarge, color = colors.onHero)
                    }
                } else {
                    Text(
                        stringResource(R.string.home_no_budget),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onHero,
                    )
                }
            }
        }
    }
}

private const val RECEIPT_ALPHA = 0.85f
private const val RECEIPT_TILT = 12f
private const val TRACK_ALPHA = 0.3f

@Composable
private fun CategoryBudgetsCard(
    state: ExpensesHomeUiState,
    onBudgets: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionHeader(stringResource(R.string.home_cat_budgets), action = stringResource(R.string.home_view_all), onAction = onBudgets)
        HCard(Modifier.fillMaxWidth()) {
            val rows = state.categoryBudgets.take(HOME_BUDGET_ROWS)
            if (rows.isEmpty()) {
                Text(
                    stringResource(R.string.home_set_limits),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onBudgets)
                            .heightIn(min = 56.dp)
                            .padding(Spacing.l),
                )
            } else {
                Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    rows.forEach { BudgetRow(it, state) }
                }
            }
        }
    }
}

@Composable
internal fun BudgetRow(
    budget: CategoryBudget,
    state: ExpensesHomeUiState,
    modifier: Modifier = Modifier,
) {
    val category = state.categories[budget.categoryId]
    Row(
        modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        IconTile(category?.iconVector() ?: Icons.Outlined.AccountBalanceWallet)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category?.label().orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${budget.spent.formatted()} / ${budget.limit.formatted()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HProgressBar(budget.fraction)
        }
        PercentChip(budget.fraction)
    }
}

/** Shortcuts to the screens that have no bottom tab on the boards. */
@Composable
private fun Shortcuts(
    actions: HomeActions,
    onImport: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Shortcut(Icons.Outlined.CalendarMonth, stringResource(R.string.home_shortcut_emi), actions.onEmi, Modifier.weight(1f))
        Shortcut(Icons.Outlined.Handshake, stringResource(R.string.home_shortcut_udhaar), actions.onUdhaar, Modifier.weight(1f))
        Shortcut(Icons.Outlined.UploadFile, stringResource(R.string.home_shortcut_import), onImport, Modifier.weight(1f))
        Shortcut(Icons.Outlined.Calculate, stringResource(R.string.home_shortcut_tools), actions.onTools, Modifier.weight(1f))
    }
}

@Composable
private fun Shortcut(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        IconTile(icon, size = 40.dp)
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2)
    }
}

@Composable
private fun RecentExpenses(
    state: ExpensesHomeUiState,
    onViewAll: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val recent = state.days.flatMap { it.expenses }.take(RECENT_COUNT)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionHeader(stringResource(R.string.home_recent), action = stringResource(R.string.home_view_all), onAction = onViewAll)
        HCard(Modifier.fillMaxWidth()) {
            when {
                state.loading -> LoadingRows()
                recent.isEmpty() ->
                    EmptyState(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.home_empty_body),
                    )
                else ->
                    Column(Modifier.padding(vertical = Spacing.s)) {
                        recent.forEachIndexed { i, expense ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.l))
                            RecentRow(expense, state, onClick = { onOpen(expense.id) })
                        }
                    }
            }
        }
    }
}

@Composable
private fun RecentRow(
    expense: Expense,
    state: ExpensesHomeUiState,
    onClick: () -> Unit,
) {
    val category = state.categories[expense.categoryId]
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        CategoryTile(category)
        Column(Modifier.weight(1f)) {
            Text(
                expense.note ?: category?.label().orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                dayLabel(expense.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("- ", style = MaterialTheme.typography.titleSmall, modifier = Modifier.clearAndSetSemantics {})
        HAmountText(amount = expense.amount, style = MaterialTheme.typography.titleSmall)
    }
}

/** Grey placeholder bars while the first data loads (board "Loading" state). */
@Composable
private fun LoadingRows() {
    Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        repeat(LOADING_ROWS) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Box(
                        Modifier
                            .fillMaxWidth(PLACEHOLDER_WIDE)
                            .height(12.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(PLACEHOLDER_NARROW)
                            .height(10.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    )
                }
            }
        }
    }
}

private const val LOADING_ROWS = 3
private const val PLACEHOLDER_WIDE = 0.7f
private const val PLACEHOLDER_NARROW = 0.4f
