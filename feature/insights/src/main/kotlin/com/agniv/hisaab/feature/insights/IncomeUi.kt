package com.agniv.hisaab.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.feature.insights.R

/**
 * Optional monthly income for the savings insight. Off until the user adds it, with the privacy promise right
 * there: it stays on this phone, is not shared with the family, and never goes to a server or AI.
 */
@Composable
internal fun IncomeCard(
    income: Money?,
    onSet: (Money?) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.income_title), style = MaterialTheme.typography.titleMedium)
            if (income == null) {
                Text(stringResource(R.string.income_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { editing = true }) { Text(stringResource(R.string.income_add)) }
            } else {
                Text(stringResource(R.string.income_saved, income.formatted()), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    TextButton(onClick = { editing = true }) { Text(stringResource(R.string.insights_change)) }
                    TextButton(onClick = { onSet(null) }) { Text(stringResource(R.string.income_forget)) }
                }
            }
            Text(
                stringResource(R.string.income_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (editing) {
        IncomeDialog(
            current = income,
            onSave = {
                onSet(it)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun IncomeDialog(
    current: Money?,
    onSave: (Money) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current?.rupeesPart?.toString().orEmpty()) }
    val amount = Money.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.income_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(MAX_DIGITS) },
                label = { Text(stringResource(R.string.income_amount)) },
                prefix = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { amount?.let(onSave) }, enabled = amount != null && amount.paise > 0) {
                Text(stringResource(R.string.insights_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.insights_cancel)) } },
    )
}

private const val MAX_DIGITS = 9
