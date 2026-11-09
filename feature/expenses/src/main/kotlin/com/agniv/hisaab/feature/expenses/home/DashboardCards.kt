package com.agniv.hisaab.feature.expenses.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.DonutSlice
import com.agniv.hisaab.core.designsystem.component.HAmountText
import com.agniv.hisaab.core.designsystem.component.HDonut
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.usecase.Share
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.ui.emoji
import com.agniv.hisaab.feature.expenses.ui.label

private const val TOP_CATEGORIES = 4
private const val OTHERS_COLOR = 0xFF9E9E9E

/** Top categories donut (DESIGN.md §7.3). Shown only when the month has spending. */
@Composable
internal fun TopCategoriesCard(state: ExpensesHomeUiState) {
    val shares = state.summary?.byCategory.orEmpty()
    if (shares.isEmpty()) return
    val top = shares.take(TOP_CATEGORIES)
    val rest = shares.drop(TOP_CATEGORIES).sumOf { it.total.paise }
    val slices =
        top.map { DonutSlice(it.total.paise, Color(state.categories[it.id]?.color ?: OTHERS_COLOR)) } +
            listOfNotNull(rest.takeIf { it > 0 }?.let { DonutSlice(it, Color(OTHERS_COLOR)) })
    val description =
        top.joinToString(", ") { "${state.categories[it.id]?.name.orEmpty()} ${it.total.formatted()}" }
    DashboardCard(title = stringResource(R.string.dash_top_categories)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            HDonut(slices = slices, description = description)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                top.forEach { share ->
                    val category = state.categories[share.id]
                    LegendRow(
                        color = Color(category?.color ?: OTHERS_COLOR),
                        label = category?.let { "${it.emoji()} ${it.label()}" }.orEmpty(),
                        share = share,
                    )
                }
                if (rest > 0) {
                    LegendRow(
                        color = Color(OTHERS_COLOR),
                        label = stringResource(R.string.dash_rest),
                        share = Share("rest", Money(rest)),
                    )
                }
            }
        }
    }
}

/** Per-member bars: who paid how much this month. Hidden for a one-person family. */
@Composable
internal fun WhoSpentCard(state: ExpensesHomeUiState) {
    val shares = state.summary?.byMember.orEmpty()
    if (shares.size < 2) return
    val max = shares.maxOf { it.total.paise }.coerceAtLeast(1)
    DashboardCard(title = stringResource(R.string.dash_who_spent)) {
        shares.forEach { share ->
            val name = state.members[share.id]?.name ?: stringResource(R.string.dash_unknown_member)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    HAmountText(amount = share.total, style = MaterialTheme.typography.bodyLarge)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(share.total.paise.toFloat() / max)
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendRow(
    color: Color,
    label: String,
    share: Share,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        HAmountText(amount = share.total, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DashboardCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
