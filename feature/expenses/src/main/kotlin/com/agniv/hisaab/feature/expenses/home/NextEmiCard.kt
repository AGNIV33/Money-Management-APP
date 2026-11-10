package com.agniv.hisaab.feature.expenses.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.loan.DueStatus
import com.agniv.hisaab.core.domain.loan.NextEmi
import com.agniv.hisaab.core.domain.usecase.LoanItem
import com.agniv.hisaab.feature.expenses.R
import java.time.format.DateTimeFormatter

/** DESIGN.md §7.3: "Next EMI: Car loan ₹8,200 • in 3 days [Mark paid]". */
@Composable
internal fun NextEmiCard(
    item: LoanItem,
    onMarkPaid: () -> Unit,
) {
    val next = item.progress.next ?: return
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.next_emi_title), style = MaterialTheme.typography.labelLarge)
                Text(
                    item.loan.lender + " • " + item.loan.emi.formatted(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(dueText(next), style = MaterialTheme.typography.bodyMedium, color = dueColor(next.status))
            }
            FilledTonalButton(onClick = onMarkPaid) { Text("✓ " + stringResource(R.string.next_emi_mark_paid)) }
        }
    }
}

@Composable
private fun dueText(next: NextEmi): String =
    when (next.status) {
        DueStatus.OVERDUE -> stringResource(R.string.next_emi_overdue, -next.daysLeft.toInt())
        DueStatus.TODAY -> stringResource(R.string.next_emi_today)
        DueStatus.SOON -> stringResource(R.string.next_emi_in_days, next.daysLeft.toInt())
        DueStatus.LATER ->
            stringResource(
                R.string.next_emi_on,
                next.date.format(DateTimeFormatter.ofPattern("d MMM", LocalConfiguration.current.locales[0])),
            )
    }

@Composable
private fun dueColor(status: DueStatus): Color =
    when (status) {
        DueStatus.OVERDUE -> MaterialTheme.colorScheme.error
        DueStatus.TODAY, DueStatus.SOON -> HisaabTheme.extendedColors.warning
        DueStatus.LATER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
