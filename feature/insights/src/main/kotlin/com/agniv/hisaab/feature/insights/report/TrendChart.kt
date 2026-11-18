package com.agniv.hisaab.feature.insights.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.motion.animateFill
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.ml.insight.MonthTotal
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/** "₹24K" / "₹1.2L" for chart axes, where full amounts would not fit. */
internal fun Money.compact(): String {
    val rupees = paise / PAISE
    return when {
        rupees >= LAKH -> "₹" + trim(rupees / LAKH.toDouble()) + "L"
        rupees >= THOUSAND -> "₹" + trim(rupees / THOUSAND.toDouble()) + "K"
        else -> "₹$rupees"
    }
}

private fun trim(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)

private const val PAISE = 100L
private const val THOUSAND = 1_000L
private const val LAKH = 100_000L

/**
 * Board line chart "Spending Trend (Last 6 Months)": ₹ axis on the left, month labels below, soft green area,
 * the last point labelled in a green bubble. The line draws in from the left on first show. TalkBack reads every
 * month with its total instead of the drawing.
 */
@Composable
internal fun TrendChart(
    trend: List<MonthTotal>,
    modifier: Modifier = Modifier,
) {
    if (trend.isEmpty()) return
    val locale = LocalConfiguration.current.locales[0]
    val fmt = remember(locale) { DateTimeFormatter.ofPattern("MMM", locale) }
    val maxPaise = trend.maxOf { it.total.paise }.coerceAtLeast(1)
    // Round the top of the axis up to a tidy step so the labels read ₹10K, ₹20K, ...
    val step = niceStep(maxPaise)
    val top = (ceil(maxPaise / step.toDouble()).toLong()).coerceAtLeast(1) * step
    val ticks = (0..AXIS_STEPS).map { Money(top * it / AXIS_STEPS) }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val reveal by animateFill(1f)
    val spoken = trend.joinToString { "${it.month.format(fmt)} ${it.total.formatted()}" }
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .width(AXIS_W.dp)
                        .height(CHART_H.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    ticks.reversed().forEach {
                        Text(
                            it.compact(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .clearAndSetSemantics {
                                    },
                        )
                    }
                }
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(CHART_H.dp)
                        .clearAndSetSemantics {},
                ) {
                    val slot = size.width / trend.size
                    val pad = 6.dp.toPx()
                    val usable = size.height - 2 * pad
                    for (i in 0..AXIS_STEPS) {
                        val y = pad + usable * i / AXIS_STEPS
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }
                    val points =
                        trend.mapIndexed { i, m ->
                            Offset(slot * i + slot / 2, pad + usable * (1f - m.total.paise.toFloat() / top))
                        }
                    val shown = (points.size * reveal).toInt().coerceIn(1, points.size)
                    val visible = points.take(shown)
                    val area =
                        Path().apply {
                            moveTo(visible.first().x, size.height - pad)
                            visible.forEach { lineTo(it.x, it.y) }
                            lineTo(visible.last().x, size.height - pad)
                            close()
                        }
                    drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = AREA_ALPHA), Color.Transparent)))
                    val path =
                        Path().apply {
                            moveTo(visible.first().x, visible.first().y)
                            visible.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                    drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    visible.forEach {
                        drawCircle(line, 5.dp.toPx(), it)
                        drawCircle(surface, 2.5.dp.toPx(), it)
                    }
                }
            }
            // Bubble with this month's total over the last point.
            Text(
                trend.last().total.formatted(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(horizontal = Spacing.s, vertical = 2.dp)
                        .clearAndSetSemantics {},
            )
        }
        Row(Modifier.padding(start = AXIS_W.dp)) {
            trend.forEach {
                Text(
                    it.month.format(fmt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .weight(1f)
                            .clearAndSetSemantics {},
                )
            }
        }
    }
}

private const val AXIS_STEPS = 4
private const val AXIS_W = 44
private const val CHART_H = 160
private const val AREA_ALPHA = 0.18f

/** A step (in paise) that makes 4 tidy axis labels covering [max]: ₹1K, ₹2K, ₹2.5K, ₹5K, ₹10K, ... */
internal fun niceStep(max: Long): Long {
    var magnitude = RUPEE_STEP
    while (true) {
        for (m in STEP_FACTORS) {
            val step = (magnitude * m).toLong()
            if (step * AXIS_STEPS >= max) return step
        }
        magnitude *= TEN
    }
}

@Suppress("MagicNumber") // 1, 2, 2.5 and 5 make round axis labels.
private val STEP_FACTORS = listOf(1.0, 2.0, 2.5, 5.0)
private const val RUPEE_STEP = 100_000L // ₹1,000
private const val TEN = 10
