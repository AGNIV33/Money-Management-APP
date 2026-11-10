package com.agniv.hisaab.feature.loans

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.loan.DueStatus
import com.agniv.hisaab.core.domain.loan.NextEmi
import com.agniv.hisaab.core.domain.model.LoanType
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.usecase.LoanItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal fun LoanType.emoji(): String =
    when (this) {
        LoanType.HOME -> "🏠"
        LoanType.CAR -> "🚗"
        LoanType.BIKE -> "🏍️"
        LoanType.PERSONAL -> "👤"
        LoanType.EDUCATION -> "🎓"
        LoanType.GOLD -> "🪙"
        LoanType.CONSUMER -> "📱"
        LoanType.CREDIT_CARD -> "💳"
        LoanType.OTHER -> "📄"
    }

@StringRes
internal fun LoanType.labelRes(): Int =
    when (this) {
        LoanType.HOME -> R.string.loan_type_home
        LoanType.CAR -> R.string.loan_type_car
        LoanType.BIKE -> R.string.loan_type_bike
        LoanType.PERSONAL -> R.string.loan_type_personal
        LoanType.EDUCATION -> R.string.loan_type_education
        LoanType.GOLD -> R.string.loan_type_gold
        LoanType.CONSUMER -> R.string.loan_type_consumer
        LoanType.CREDIT_CARD -> R.string.loan_type_credit_card
        LoanType.OTHER -> R.string.loan_type_other
    }

@Composable
@ReadOnlyComposable
internal fun LocalDate.shortText(): String =
    format(DateTimeFormatter.ofPattern("d MMM yyyy", LocalConfiguration.current.locales[0]))

/** Words as well as colour (DESIGN.md: never colour alone). */
@Composable
internal fun dueText(next: NextEmi): String =
    when (next.status) {
        DueStatus.OVERDUE -> stringResource(R.string.due_overdue, -next.daysLeft.toInt())
        DueStatus.TODAY -> stringResource(R.string.due_today)
        DueStatus.SOON -> stringResource(R.string.due_in_days, next.daysLeft.toInt())
        DueStatus.LATER -> stringResource(R.string.due_on, next.date.shortText())
    }

@Composable
internal fun dueColor(status: DueStatus): Color =
    when (status) {
        DueStatus.OVERDUE -> MaterialTheme.colorScheme.error
        DueStatus.TODAY, DueStatus.SOON -> HisaabTheme.extendedColors.warning
        DueStatus.LATER -> MaterialTheme.colorScheme.onSurfaceVariant
    }

/** DESIGN.md `HLoanCard`: type icon, lender, EMI, next due badge, paid/total bar. */
@Composable
internal fun LoanCard(
    item: LoanItem,
    onClick: () -> Unit,
) {
    val loan = item.loan
    val progress = item.progress
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(loan.type.emoji(), style = MaterialTheme.typography.headlineSmall)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = Spacing.m),
                ) {
                    Text(loan.lender, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(loan.type.labelRes()) +
                            if (loan.visibility == Visibility.PRIVATE) "  " + stringResource(R.string.loan_private) else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.loan_emi_line, loan.emi.formatted()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            PaidBar(progress.paidFraction)
            Row {
                Text(
                    stringResource(R.string.loan_paid_of, progress.paidCount, progress.tenureMonths),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                val next = progress.next
                if (next == null) {
                    Text(
                        stringResource(R.string.loans_closed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HisaabTheme.extendedColors.success,
                    )
                } else {
                    Text(dueText(next), style = MaterialTheme.typography.bodyMedium, color = dueColor(next.status))
                }
            }
        }
    }
}

/**
 * Paid share of a loan. Not HProgressBar: that one warns as it fills (budgets), but a loan filling up
 * is good news, so this stays green.
 */
@Composable
internal fun PaidBar(fraction: Float) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(10.dp)
                .background(HisaabTheme.extendedColors.success, shape),
        )
    }
}
