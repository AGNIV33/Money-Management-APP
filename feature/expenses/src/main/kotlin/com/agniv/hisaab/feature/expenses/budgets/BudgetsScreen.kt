package com.agniv.hisaab.feature.expenses.budgets

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.EmptyState
import com.agniv.hisaab.core.designsystem.component.HAmountText
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.HProgressBar
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.component.MonthPickerChip
import com.agniv.hisaab.core.designsystem.component.MoreButton
import com.agniv.hisaab.core.designsystem.component.PercentChip
import com.agniv.hisaab.core.designsystem.component.TintSummaryCard
import com.agniv.hisaab.core.designsystem.theme.CardAmount
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.usecase.budgetFraction
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.home.BudgetRow
import com.agniv.hisaab.feature.expenses.home.ExpensesHomeUiState
import com.agniv.hisaab.feature.expenses.home.ExpensesHomeViewModel
import com.agniv.hisaab.feature.expenses.home.HomeEvent

/**
 * Board "Budgets": month picker, total budget, every category limit with its bar and percent. Admins get the +
 * button (Set limit sheet) and the ⋮ menu (total budget); everyone else sees a "view only" chip. Tapping a row opens
 * that category's detail.
 */
@Composable
fun BudgetsScreen(
    onBack: () -> Unit,
    onCategory: (categoryId: String, month: String) -> Unit,
    viewModel: ExpensesHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // null = closed, "" = new limit, otherwise the category being changed.
    var limitFor by rememberSaveable { mutableStateOf<String?>(null) }
    var editTotal by rememberSaveable { mutableStateOf(false) }
    var menu by rememberSaveable { mutableStateOf(false) }
    // Android 13+ needs the user's OK before budget alerts can show; asked right after a limit is set.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val askIfNeeded = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        topBar = {
            KinlyTopBar(title = stringResource(R.string.budgets_title), onBack = onBack) {
                if (state.canEditBudget) {
                    MoreButton(onClick = { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.budgets_set_total)) },
                            onClick = {
                                menu = false
                                editTotal = true
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (state.canEditBudget && state.categoryBudgets.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { limitFor = "" },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.budgets_set_limit)) }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            item(key = "head") {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    if (!state.canEditBudget && !state.loading) ViewOnlyChip()
                    MonthPickerChip(
                        month = state.month,
                        current = state.currentMonth,
                        onPick = { viewModel.onEvent(HomeEvent.SetMonth(it)) },
                    )
                }
            }
            item(key = "total") { TotalCard(state) }
            if (state.categoryBudgets.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        title = stringResource(R.string.budgets_empty_title),
                        body = stringResource(R.string.budgets_empty_body),
                        action = stringResource(R.string.budgets_set_limit).takeIf { state.canEditBudget },
                        onAction = { limitFor = "" },
                    )
                }
            } else {
                item(key = "title") { Text(stringResource(R.string.home_cat_budgets), style = MaterialTheme.typography.titleMedium) }
                item(key = "rows") {
                    HCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = Spacing.s)) {
                            state.categoryBudgets.forEach { budget ->
                                BudgetRow(
                                    budget,
                                    state,
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onCategory(budget.categoryId, state.month.toString()) }
                                        .heightIn(min = 72.dp)
                                        .padding(horizontal = Spacing.l, vertical = Spacing.s),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    limitFor?.let { id ->
        SetLimitSheet(
            state = state,
            categoryId = id.ifEmpty { null },
            onSave = { categoryId, limit ->
                viewModel.onEvent(HomeEvent.SetCategoryLimit(categoryId, limit))
                if (limit != null) askIfNeeded()
                limitFor = null
            },
            onDismiss = { limitFor = null },
        )
    }
    if (editTotal) {
        TotalBudgetDialog(
            current = state.budget,
            onSave = {
                viewModel.onEvent(HomeEvent.SetBudget(it))
                if (it != null) askIfNeeded()
                editTotal = false
            },
            onDismiss = { editTotal = false },
        )
    }
}

@Composable
private fun ViewOnlyChip() {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            Icons.Outlined.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Text(
            stringResource(R.string.budgets_view_only),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** "Total Budget ₹24,500 / ₹40,000" with its bar; "₹0 / ₹0, No limits set yet" when nothing is set. */
@Composable
private fun TotalCard(state: ExpensesHomeUiState) {
    val spent = state.summary?.total ?: Money.ZERO
    val budget = state.budget
    TintSummaryCard(icon = Icons.Outlined.AccountBalanceWallet) {
        Text(
            stringResource(R.string.budgets_total),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            HAmountText(spent, style = CardAmount, countUp = true)
            Text(
                " / " + (budget ?: Money.ZERO).formatted(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (budget == null || budget.isZero) {
            Text(
                stringResource(R.string.budgets_no_total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            val fraction = budgetFraction(spent, budget)
            Row(
                Modifier.padding(top = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                HProgressBar(fraction, Modifier.weight(1f))
                PercentChip(fraction)
            }
        }
    }
}
