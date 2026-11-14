package com.agniv.hisaab.feature.expenses.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.ml.insight.CategoryRow
import com.agniv.hisaab.core.ml.insight.MonthTotal
import com.agniv.hisaab.core.ml.insight.MonthlyReport
import com.agniv.hisaab.core.ml.insight.ReportGrade
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.ui.emoji
import com.agniv.hisaab.feature.expenses.ui.label
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val BarHeight = 8.dp
private val TrendHeight = 72.dp
private val BarShape = RoundedCornerShape(4.dp)
private const val UNFINISHED_ALPHA = 0.5f

/**
 * DESIGN.md 7.10 Monthly Report Card. Bars are plain boxes, not a chart library: nothing to add to the APK
 * and cheap to draw on a 2 GB phone.
 */
@Composable
internal fun MonthlyReportCard(
    report: MonthlyReport,
    categories: Map<String, Category>,
) {
    val locale = LocalConfiguration.current.locales[0]
    val monthName = report.month.format(DateTimeFormatter.ofPattern("MMMM", locale))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    stringResource(R.string.report_card_title, monthName),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                report.grade?.let { Text(it.emoji(), style = MaterialTheme.typography.headlineMedium) }
            }
            report.grade?.let {
                Text(stringResource(it.meaning()), style = MaterialTheme.typography.bodyMedium)
            }
            Text(totalLine(report), style = MaterialTheme.typography.bodyLarge)
            if (report.categories.isNotEmpty()) {
                Legend()
                val max = report.categories.maxOf { maxOf(it.thisMonth.paise, it.lastSamePeriod.paise) }.coerceAtLeast(1)
                report.categories.forEach { CategoryBars(it, categories[it.categoryId], max) }
                if (report.others.paise > 0) {
                    Text(
                        stringResource(R.string.report_card_others, report.others.formatted()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(stringResource(R.string.report_card_trend), style = MaterialTheme.typography.titleSmall)
            Trend(report.trend)
        }
    }
}

@Composable
private fun totalLine(report: MonthlyReport): String {
    val change = report.changePercent
    return when {
        change == null -> stringResource(R.string.report_card_spent, report.spent.formatted())
        change >= 0 -> stringResource(R.string.report_card_spent_more, report.spent.formatted(), change)
        else -> stringResource(R.string.report_card_spent_less, report.spent.formatted(), abs(change))
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(MaterialTheme.colorScheme.primary, stringResource(R.string.report_card_this_month))
        LegendDot(MaterialTheme.colorScheme.outlineVariant, stringResource(R.string.report_card_last_month))
    }
}

@Composable
private fun LegendDot(
    color: Color,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(
            Modifier
                .width(12.dp)
                .height(BarHeight)
                .background(color, BarShape),
        )
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryBars(
    row: CategoryRow,
    category: Category?,
    max: Long,
) {
    val name = category?.let { it.emoji() + " " + it.label() } ?: stringResource(R.string.insights_this_category)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(row.thisMonth.formatted(), style = MaterialTheme.typography.labelLarge)
        }
        Bar(row.thisMonth, max, MaterialTheme.colorScheme.primary)
        Bar(row.lastSamePeriod, max, MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun Bar(
    amount: Money,
    max: Long,
    color: Color,
) {
    val fraction = (amount.paise.toFloat() / max).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .background(MaterialTheme.colorScheme.surfaceVariant, BarShape),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(color, BarShape),
            )
        }
    }
}

/** Six month totals as small columns; this month is lighter because it is not finished yet. */
@Composable
private fun Trend(trend: List<MonthTotal>) {
    val locale = LocalConfiguration.current.locales[0]
    val fmt = DateTimeFormatter.ofPattern("MMM", locale)
    val max = trend.maxOfOrNull { it.total.paise }?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        trend.forEachIndexed { i, m ->
            val primary = MaterialTheme.colorScheme.primary
            val barColor = if (i == trend.lastIndex) primary.copy(alpha = UNFINISHED_ALPHA) else primary
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(TrendHeight).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val fraction = (m.total.paise.toFloat() / max).coerceIn(0f, 1f)
                    if (fraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .background(barColor, BarShape),
                        )
                    }
                }
                Text(m.month.format(fmt), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

private fun ReportGrade.emoji() =
    when (this) {
        ReportGrade.GOOD -> "😊"
        ReportGrade.OKAY -> "🙂"
        ReportGrade.WATCH -> "😟"
    }

private fun ReportGrade.meaning() =
    when (this) {
        ReportGrade.GOOD -> R.string.report_grade_good
        ReportGrade.OKAY -> R.string.report_grade_okay
        ReportGrade.WATCH -> R.string.report_grade_watch
    }
