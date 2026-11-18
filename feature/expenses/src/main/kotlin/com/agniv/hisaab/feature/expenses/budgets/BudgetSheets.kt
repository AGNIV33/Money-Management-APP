package com.agniv.hisaab.feature.expenses.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.designsystem.component.HButton
import com.agniv.hisaab.core.designsystem.component.HOutlinedButton
import com.agniv.hisaab.core.designsystem.component.KinlySelectField
import com.agniv.hisaab.core.designsystem.component.KinlyTextField
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.home.ExpensesHomeUiState
import com.agniv.hisaab.feature.expenses.ui.iconVector
import com.agniv.hisaab.feature.expenses.ui.label

/** Board "Set Limit" bottom sheet: category drop-down, monthly limit, Cancel / Save (and Remove for an existing one). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetLimitSheet(
    state: ExpensesHomeUiState,
    categoryId: String?,
    onSave: (String, Money?) -> Unit,
    onDismiss: () -> Unit,
) {
    val taken = state.categoryBudgets.map { it.categoryId }.toSet()
    // A new limit offers the categories without one; changing one keeps it fixed.
    val choices =
        state.categories.values
            .filter { categoryId != null || it.id !in taken }
            .sortedBy { it.name }
    var picked by rememberSaveable { mutableStateOf(categoryId ?: choices.firstOrNull()?.id) }
    val current = state.categoryBudgets.firstOrNull { it.categoryId == picked }?.limit
    var text by rememberSaveable(picked) { mutableStateOf(current?.rupeesPart?.toString().orEmpty()) }
    val amount = Money.parse(text)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.budgets_set_limit),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.voice_close)) }
            }
            Text(stringResource(R.string.add_expense_category), style = MaterialTheme.typography.titleSmall)
            val category = state.categories[picked]
            KinlySelectField(
                value = category?.label().orEmpty(),
                options = if (categoryId == null) choices else emptyList(),
                optionLabel = { it.label() },
                optionIcon = { it.iconVector() },
                onSelect = { picked = it.id },
                leadingIcon = category?.iconVector(),
            )
            Text(stringResource(R.string.budgets_monthly_limit), style = MaterialTheme.typography.titleSmall)
            KinlyTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(MAX_DIGITS) },
                leadingIcon = Icons.Filled.CurrencyRupee,
                keyboardType = KeyboardType.Number,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                HOutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                HButton(
                    onClick = { picked?.let { onSave(it, amount) } },
                    enabled = picked != null && amount != null && amount.paise > 0,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.budgets_save)) }
            }
            if (categoryId != null) {
                TextButton(onClick = { onSave(categoryId, null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.budgets_remove_limit), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Admin ⋮ → total monthly budget for the family. */
@Composable
internal fun TotalBudgetDialog(
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
            KinlyTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(MAX_DIGITS) },
                label = stringResource(R.string.budget_amount),
                leadingIcon = Icons.Filled.CurrencyRupee,
                keyboardType = KeyboardType.Number,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(amount) }, enabled = amount != null && amount.paise > 0) {
                Text(stringResource(R.string.budgets_save))
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
