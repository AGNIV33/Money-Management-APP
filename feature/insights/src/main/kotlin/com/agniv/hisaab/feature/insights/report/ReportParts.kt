package com.agniv.hisaab.feature.insights.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.DonutSlice
import com.agniv.hisaab.core.designsystem.component.GoldCard
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.HDonut
import com.agniv.hisaab.core.designsystem.component.categoryIcon
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.feature.insights.R
import com.agniv.hisaab.feature.insights.label

private const val OTHER_COLOR = 0xFF9E9E9E

internal fun Slice.color(categories: Map<String, Category>): Color =
    Color(categoryId?.let { categories[it]?.color } ?: OTHER_COLOR)

@Composable
internal fun Slice.name(categories: Map<String, Category>): String =
    categoryId?.let { categories[it]?.label() } ?: stringResource(R.string.report_other)

/** Board donut with the total in the middle and a legend: dot, name, percent. */
@Composable
internal fun CategoryDonut(
    slices: List<Slice>,
    categories: Map<String, Category>,
    total: Money,
) {
    val description = slices.joinToString { "${it.categoryId?.let { id -> categories[id]?.name } ?: "Other"} ${it.percent}%" }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Box(contentAlignment = Alignment.Center) {
            HDonut(
                slices =
                    slices.map {
                        DonutSlice(it.total.paise, it.color(categories))
                    },
                description = description,
                size = DONUT.dp,
                thickness = 22.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics {}) {
                Text(total.formatted(), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.report_total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            slices.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics {}) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(s.color(categories), CircleShape),
                    )
                    Text(
                        s.name(categories),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).padding(start = Spacing.s),
                    )
                    Text("${s.percent}%", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private const val DONUT = 132

/** Board breakdown rows: icon, name, amount, percent. */
@Composable
internal fun CategoryRows(
    slices: List<Slice>,
    categories: Map<String, Category>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        slices.forEach { s ->
            val c = s.categoryId?.let { categories[it] }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Icon(categoryIcon(c?.icon ?: ""), contentDescription = null, tint = s.color(categories), modifier = Modifier.size(20.dp))
                Text(s.name(categories), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(s.total.formatted(), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${s.percent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** One of the three small metric cards (Savings / EMI Share / Total Spent). */
@Composable
internal fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    sub: String?,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
) {
    HCard(modifier, containerColor = container) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.m, horizontal = Spacing.s)
                .semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(icon, contentDescription = null, tint = HisaabTheme.extendedColors.gold)
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            Text(value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            sub?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Gold "Tips for next month" card with short bullets. */
@Composable
internal fun TipsCard(
    tips: List<Tip>,
    categories: Map<String, Category>,
) {
    if (tips.isEmpty()) return
    val colors = HisaabTheme.extendedColors
    GoldCard {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = colors.gold)
                Text(
                    stringResource(R.string.report_tips_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onGoldContainer,
                )
            }
            tips.forEach { tip ->
                Text("•  " + tipText(tip, categories), style = MaterialTheme.typography.bodyMedium, color = colors.onGoldContainer)
            }
        }
    }
}

@Composable
private fun tipText(
    tip: Tip,
    categories: Map<String, Category>,
): String {
    fun name(id: String) = categories[id]?.name ?: ""
    return when (tip) {
        is Tip.BigShare ->
            stringResource(
                R.string.report_tip_share,
                categories[tip.categoryId]?.label() ?: name(tip.categoryId),
                tip.percent,
            )
        is Tip.KeepUnder -> stringResource(R.string.report_tip_keep_under, tip.percent)
        is Tip.Saved -> stringResource(R.string.report_tip_saved, tip.percent)
        is Tip.LessThanLast -> stringResource(R.string.report_tip_less, tip.percent)
        is Tip.MoreThanLast -> stringResource(R.string.report_tip_more, tip.percent)
    }
}
