package com.agniv.hisaab.feature.expenses.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.HProgressBar
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.usecase.BudgetLevel
import com.agniv.hisaab.core.domain.usecase.CategoryBudget
import com.agniv.hisaab.core.domain.usecase.budgetFraction
import com.agniv.hisaab.core.domain.usecase.budgetLevel
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.ui.emoji
import com.agniv.hisaab.feature.expenses.ui.label

/**
 * Family monthly budget: progress bar when set, a "Set budget" prompt for admins when not (PRD F6).
 * Below it, optional per-category limits with their own bars; admins add, change or remove them.
 */
@Composable
internal fun BudgetCard(
    state: ExpensesHomeUiState,
    onSetBudget: (Money?) -> Unit,
    onSetCategoryLimit: (String, Money?) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    // null = closed, "" = picking a new category, otherwise the category being changed.
    var limitFor by remember { mutableStateOf<String?>(null) }
    val budget = state.budget
    val spent = state.summary?.total ?: Money.ZERO
    if (budget == null && state.categoryBudgets.isEmpty() && !state.canEditBudget) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (budget == null) {
                Text(stringResource(R.string.budget_title), style = MaterialTheme.typography.titleMedium)
                if (state.canEditBudget) {
                    Text(stringResource(R.string.budget_none_hint), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { editing = true }) { Text(stringResource(R.string.budget_set)) }
                }
            } else {
                BudgetProgress(spent, budget, state.canEditBudget) { editing = true }
            }
            CategoryLimits(state) { limitFor = it }
        }
    }
    limitFor?.let { id ->
        CategoryLimitDialog(
            state = state,
            categoryId = id.ifEmpty { null },
            onSave = { categoryId, limit ->
                onSetCategoryLimit(categoryId, limit)
                limitFor = null
            },
            onDismiss = { limitFor = null },
        )
    }
    if (editing) {
        BudgetDialog(
            current = budget,
            onSave = {
                onSetBudget(it)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun BudgetProgress(
    spent: Money,
    budget: Money,
    canEdit: Boolean,
    onEdit: () -> Unit,
) {
    val level = budgetLevel(spent, budget)
    val percent = (budgetFraction(spent, budget) * PERCENT).toInt()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.budget_title), style = MaterialTheme.typography.titleMedium)
        if (canEdit) TextButton(onClick = onEdit) { Text(stringResource(R.string.budget_edit)) }
    }
    HProgressBar(fraction = budgetFraction(spent, budget))
    // Words and ▲ as well as colour: colour alone is not enough (DESIGN.md §10).
    val left = budget - spent
    Text(
        text =
            when (level) {
                BudgetLevel.OVER -> stringResource(R.string.budget_over_by, "▲", (-left).formatted(), percent)
                BudgetLevel.WARNING -> stringResource(R.string.budget_almost, left.formatted(), percent)
                BudgetLevel.OK -> stringResource(R.string.budget_left, left.formatted(), percent)
            },
        style = MaterialTheme.typography.bodyMedium,
        color =
            when (level) {
                BudgetLevel.OVER -> MaterialTheme.colorScheme.error
                BudgetLevel.WARNING -> HisaabTheme.extendedColors.warning
                BudgetLevel.OK -> MaterialTheme.colorScheme.onSurfaceVariant
            },
    )
    Text(
        stringResource(R.string.budget_of, budget.formatted()),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val PERCENT = 100

@Composable
private fun BudgetDialog(
    current: Money?,
    onSave: (Money?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current?.rupeesPart?.toString().orEmpty()) }
    val amount = Money.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(MAX_DIGITS) },
                label = { Text(stringResource(R.string.budget_amount)) },
                prefix = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(amount) }, enabled = amount != null && amount.paise > 0) {
                Text(stringResource(R.string.add_expense_save))
            }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.budget_remove)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

private const val MAX_DIGITS = 9

@Composable
private fun CategoryLimits(
    state: ExpensesHomeUiState,
    onEdit: (String) -> Unit,
) {
    if (state.categoryBudgets.isEmpty() && !state.canEditBudget) return
    HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
    Text(stringResource(R.string.cat_limits_title), style = MaterialTheme.typography.titleSmall)
    if (state.categoryBudgets.isEmpty()) {
        Text(
            stringResource(R.string.cat_limits_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.categoryBudgets.forEach { item ->
        val category = state.categories[item.categoryId] ?: return@forEach
        CategoryLimitRow(item, "${category.emoji()} ${category.label()}", state.canEditBudget) { onEdit(item.categoryId) }
    }
    if (state.canEditBudget) {
        TextButton(onClick = { onEdit("") }) { Text(stringResource(R.string.cat_limit_add)) }
    }
}

@Composable
private fun CategoryLimitRow(
    item: CategoryBudget,
    name: String,
    canEdit: Boolean,
    onEdit: () -> Unit,
) {
    val left = item.limit - item.spent
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (canEdit) Modifier.clickable(onClick = onEdit) else Modifier)
            .padding(vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.cat_limit_of, item.spent.formatted(), item.limit.formatted()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HProgressBar(fraction = item.fraction)
        // Words as well as colour (DESIGN.md §10), only when it needs attention.
        when (item.level) {
            BudgetLevel.OVER ->
                Text(
                    stringResource(R.string.cat_limit_over, "▲", (-left).formatted()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            BudgetLevel.WARNING ->
                Text(
                    stringResource(R.string.cat_limit_almost, left.formatted()),
                    style = MaterialTheme.typography.bodySmall,
                    color = HisaabTheme.extendedColors.warning,
                )
            BudgetLevel.OK -> Unit
        }
    }
}

@Composable
private fun CategoryLimitDialog(
    state: ExpensesHomeUiState,
    categoryId: String?,
    onSave: (String, Money?) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(categoryId) }
    val current = state.categoryBudgets.firstOrNull { it.categoryId == picked }?.limit
    var text by remember(picked) { mutableStateOf(current?.rupeesPart?.toString().orEmpty()) }
    val amount = Money.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cat_limit_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (categoryId == null) {
                    CategoryPicker(state, picked) { picked = it }
                } else {
                    state.categories[categoryId]?.let {
                        Text("${it.emoji()} ${it.label()}", style = MaterialTheme.typography.titleSmall)
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(MAX_DIGITS) },
                    label = { Text(stringResource(R.string.cat_limit_amount)) },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            val id = picked
            TextButton(onClick = { if (id != null) onSave(id, amount) }, enabled = id != null && amount != null && amount.paise > 0) {
                Text(stringResource(R.string.add_expense_save))
            }
        },
        dismissButton = {
            Row {
                if (categoryId != null) TextButton(onClick = { onSave(categoryId, null) }) { Text(stringResource(R.string.budget_remove)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** New limit: chips for the categories that do not have one yet. */
@Composable
private fun CategoryPicker(
    state: ExpensesHomeUiState,
    picked: String?,
    onPick: (String) -> Unit,
) {
    val taken = state.categoryBudgets.map { it.categoryId }.toSet()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        state.categories.values
            .filter { it.id !in taken }
            .sortedBy { it.name }
            .forEach { c ->
                FilterChip(selected = picked == c.id, onClick = { onPick(c.id) }, label = { Text("${c.emoji()} ${c.label()}") })
            }
    }
}
