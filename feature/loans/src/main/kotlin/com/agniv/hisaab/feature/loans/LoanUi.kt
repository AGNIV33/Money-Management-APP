package com.agniv.hisaab.feature.loans

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.HProgressBar
import com.agniv.hisaab.core.designsystem.component.IconTile
import com.agniv.hisaab.core.designsystem.motion.popIn
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

/** Board icon for each kind of loan. */
internal fun LoanType.icon(): ImageVector =
    when (this) {
        LoanType.HOME -> Icons.Outlined.Home
        LoanType.CAR -> Icons.Outlined.DirectionsCar
        LoanType.BIKE -> Icons.Outlined.TwoWheeler
        LoanType.PERSONAL -> Icons.Outlined.Person
        LoanType.EDUCATION -> Icons.Outlined.School
        LoanType.GOLD -> Icons.Outlined.Savings
        LoanType.CONSUMER -> Icons.Outlined.Devices
        LoanType.CREDIT_CARD -> Icons.Outlined.CreditCard
        LoanType.OTHER -> Icons.Outlined.Description
    }

/**
 * Board EMI card: icon, name, "₹4,000 / month", next due line, paid bar with "6 / 12". Due within a week turns the
 * card gold, overdue turns it red, a finished loan says "Completed ✓" - always in words, never colour alone.
 */
@Composable
internal fun LoanCard(
    item: LoanItem,
    onClick: () -> Unit,
) {
    val loan = item.loan
    val progress = item.progress
    val next = progress.next
    val colors = HisaabTheme.extendedColors
    val (container, onContainer) =
        when (next?.status) {
            DueStatus.OVERDUE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            DueStatus.TODAY, DueStatus.SOON -> colors.goldContainer to colors.onGoldContainer
            else -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurfaceVariant
        }
    HCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        containerColor = container,
    ) {
        Column(
            Modifier
                .padding(Spacing.l)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                IconTile(loan.type.icon())
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(loan.lender, style = MaterialTheme.typography.titleSmall)
                        if (loan.visibility == Visibility.PRIVATE) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = stringResource(R.string.loan_private),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Text(stringResource(R.string.emi_per_month, loan.emi.formatted()), style = MaterialTheme.typography.bodyMedium)
                    when {
                        next == null ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(
                                    stringResource(R.string.emi_completed, progress.paidCount, progress.tenureMonths),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.success,
                                )
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.success,
                                    modifier = Modifier.size(14.dp).popIn(),
                                )
                            }
                        next.status == DueStatus.LATER ->
                            Text(
                                stringResource(R.string.emi_next_due, next.date.shortText()),
                                style = MaterialTheme.typography.bodySmall,
                                color = onContainer,
                            )
                        else ->
                            Text(
                                dueText(next) + " (" + next.date.shortText() + ")",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (next.status == DueStatus.OVERDUE) MaterialTheme.colorScheme.error else onContainer,
                            )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                PaidBar(progress.paidFraction, Modifier.weight(1f), status = next?.status)
                Text("${progress.paidCount} / ${progress.tenureMonths}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Paid share of a loan. Green as it fills (a loan filling up is good news); gold when the next EMI is near and red
 * when one is overdue, matching the card. Fills from empty the first time it is shown.
 */
@Composable
internal fun PaidBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    status: DueStatus? = null,
) {
    HProgressBar(
        fraction = fraction,
        modifier = modifier,
        color =
            when (status) {
                DueStatus.OVERDUE -> HisaabTheme.extendedColors.over
                DueStatus.TODAY, DueStatus.SOON -> HisaabTheme.extendedColors.gold
                else -> MaterialTheme.colorScheme.primary
            },
    )
}
