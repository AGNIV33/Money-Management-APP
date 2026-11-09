package com.agniv.hisaab.core.designsystem.component

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.MoneyFormatter
import com.agniv.hisaab.core.designsystem.theme.DisplayAmount
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme

@Composable
fun HAmountText(
    amount: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    status: AmountStatus = AmountStatus.NEUTRAL,
    paise: MoneyFormatter.Paise = MoneyFormatter.Paise.WHEN_NEEDED,
) {
    val text = remember(amount, paise) { MoneyFormatter.format(amount, paise) }
    Text(
        text = text,
        modifier = modifier,
        color = amountColor(status),
        // Tabular digits keep columns of amounts aligned.
        style = style.merge(TextStyle(fontFeatureSettings = "tnum")),
        maxLines = 1,
    )
}

@Composable
private fun amountColor(status: AmountStatus): Color =
    when (status) {
        AmountStatus.NEUTRAL -> LocalContentColor.current
        AmountStatus.SUCCESS -> HisaabTheme.extendedColors.success
        AmountStatus.WARNING -> HisaabTheme.extendedColors.warning
        AmountStatus.ALERT -> MaterialTheme.colorScheme.error
    }

@Preview
@Composable
private fun HAmountTextPreview() {
    HisaabTheme {
        HAmountText(amount = Money(12_500_050), style = DisplayAmount)
    }
}
