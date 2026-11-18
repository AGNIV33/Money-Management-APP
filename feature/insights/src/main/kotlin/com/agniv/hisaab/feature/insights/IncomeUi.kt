package com.agniv.hisaab.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.designsystem.component.GoldCard
import com.agniv.hisaab.core.designsystem.component.HAmountText
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.KinlyTextField
import com.agniv.hisaab.core.designsystem.theme.CardAmount
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
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
    if (income == null) {
        // Board "Add monthly income": gold card with a switch that is off until an income is saved.
        val gold = HisaabTheme.extendedColors
        GoldCard(Modifier.toggleable(value = false, role = Role.Switch) { editing = true }) {
            Row(
                Modifier.padding(Spacing.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Icon(Icons.Outlined.Payments, contentDescription = null, tint = gold.gold)
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.income_add_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = gold.onGoldContainer,
                    )
                    Text(stringResource(R.string.income_add_body), style = MaterialTheme.typography.bodySmall, color = gold.onGoldContainer)
                }
                Switch(checked = false, onCheckedChange = null)
            }
        }
    } else {
        HCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Row(
                Modifier.padding(Spacing.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Icon(
                    Icons.Outlined.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(
                        stringResource(R.string.income_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    HAmountText(income, style = CardAmount)
                }
                TextButton(onClick = { editing = true }) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.income_edit), modifier = Modifier.padding(start = Spacing.xs))
                }
            }
        }
    }
    if (editing) {
        IncomeDialog(
            current = income,
            onSave = {
                onSet(it)
                editing = false
            },
            onForget =
                if (income != null) {
                    {
                        onSet(null)
                        editing = false
                    }
                } else {
                    null
                },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun IncomeDialog(
    current: Money?,
    onSave: (Money) -> Unit,
    onForget: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current?.rupeesPart?.toString().orEmpty()) }
    val amount = Money.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.income_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                KinlyTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(MAX_DIGITS) },
                    label = stringResource(R.string.income_amount),
                    leadingIcon = Icons.Filled.CurrencyRupee,
                    keyboardType = KeyboardType.Number,
                )
                Text(
                    stringResource(R.string.income_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { amount?.let(onSave) }, enabled = amount != null && amount.paise > 0) {
                Text(stringResource(R.string.insights_save))
            }
        },
        dismissButton = {
            Row {
                onForget?.let { TextButton(onClick = it) { Text(stringResource(R.string.income_forget)) } }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.insights_cancel)) }
            }
        },
    )
}

private const val MAX_DIGITS = 9
