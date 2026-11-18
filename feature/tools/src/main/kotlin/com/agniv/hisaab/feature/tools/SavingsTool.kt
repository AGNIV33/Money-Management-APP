package com.agniv.hisaab.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.DonutSlice
import com.agniv.hisaab.core.designsystem.component.HDonut
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.loan.LoanForm

/** What a fixed deposit or a monthly saving grows to. An estimate: no tax, no penalty, a fixed rate. */
@Composable
internal fun SavingsTool(onResult: (RecentResult?) -> Unit) {
    var monthly by rememberSaveable { mutableStateOf(false) }
    val amount = rememberSaveable { mutableStateOf("") }
    val rate = rememberSaveable { mutableStateOf("") }
    val inputs =
        LoanInputs(
            amount = amount,
            rate = rate,
            tenure = rememberSaveable { mutableStateOf("") },
            inYears = rememberSaveable { mutableStateOf(true) },
        )
    InputCard {
        Segmented(
            options = listOf(R.string.savings_lump, R.string.savings_monthly),
            selected = if (monthly) 1 else 0,
            onSelect = { monthly = it == 1 },
        )
        MoneyField(amount, if (monthly) R.string.savings_amount_monthly else R.string.savings_amount_lump)
        PercentField(rate, R.string.savings_rate)
        TenureInput(inputs)
    }
    val money = LoanForm.parseAmount(amount.value)
    val bps = LoanForm.parseRateBps(rate.value)
    val months = LoanForm.parseTenureMonths(inputs.tenure.value, inputs.inYears.value)
    val result = remember(money, bps, months, monthly) { ToolsLogic.savings(money, bps, months, monthly) }
    LaunchedEffect(result) {
        onResult(result?.let { recent(Tool.SAVINGS, it.maturity.paise, months?.toLong() ?: 0, if (monthly) 1 else 0) })
    }
    if (result == null) {
        EmptyResult(stringResource(R.string.savings_need_inputs), Icons.Outlined.Percent)
        return
    }
    val investedColor = MaterialTheme.colorScheme.primary
    val interestColor = HisaabTheme.extendedColors.gold
    ResultHero(stringResource(R.string.savings_maturity), result.maturity.formatted())
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            HDonut(
                slices = listOf(DonutSlice(result.invested.paise, investedColor), DonutSlice(result.earned.paise, interestColor)),
                description = stringResource(R.string.savings_pie_desc, result.invested.formatted(), result.earned.formatted()),
                size = 104.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Stat(stringResource(R.string.savings_invested), result.invested.formatted(), dot = investedColor)
                Stat(stringResource(R.string.savings_earned), result.earned.formatted(), dot = interestColor)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            stringResource(if (monthly) R.string.savings_note_monthly else R.string.savings_note_lump),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
