package com.agniv.hisaab.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.designsystem.theme.DisplayAmount
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing

/**
 * Title, big amount and an optional sub-line, e.g. "▼ ₹2,300 less than October".
 * Callers pass already-localised strings so this stays free of hard-coded text.
 */
@Composable
fun HSummaryCard(
    title: String,
    amount: Money,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleStatus: AmountStatus = AmountStatus.NEUTRAL,
    footer: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HAmountText(amount = amount, style = DisplayAmount)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        when (subtitleStatus) {
                            AmountStatus.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
                            AmountStatus.SUCCESS -> HisaabTheme.extendedColors.success
                            AmountStatus.WARNING -> HisaabTheme.extendedColors.warning
                            AmountStatus.ALERT -> MaterialTheme.colorScheme.error
                        },
                )
            }
            footer?.invoke()
        }
    }
}

@Preview
@Composable
private fun HSummaryCardPreview() {
    HisaabTheme {
        HSummaryCard(
            title = "This month spent",
            amount = Money.ofRupees(32_450),
            subtitle = "▼ ₹2,300 less than October",
            subtitleStatus = AmountStatus.SUCCESS,
        )
    }
}

@Preview
@Composable
private fun HSummaryCardDarkPreview() {
    HisaabTheme(darkTheme = true) {
        HSummaryCard(title = "This month spent", amount = Money.ofRupees(32_450))
    }
}
