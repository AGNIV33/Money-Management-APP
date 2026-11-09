package com.agniv.hisaab.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class DonutSlice(
    val value: Long,
    val color: Color,
)

private const val FULL_CIRCLE = 360f
private const val START_AT_TOP = -90f
private const val GAP_DEGREES = 1.5f

/**
 * Plain Canvas donut: no chart library, cheap on low-end phones.
 * [description] is read by TalkBack instead of the drawing (DESIGN.md §10: charts need a text summary).
 */
@Composable
fun HDonut(
    slices: List<DonutSlice>,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    thickness: Dp = 18.dp,
    emptyColor: Color = Color.LightGray,
) {
    val total = slices.sumOf { it.value }.toFloat()
    Canvas(
        modifier =
            modifier
                .size(size)
                .semantics { contentDescription = description },
    ) {
        val stroke = thickness.toPx()
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(stroke / 2, stroke / 2)
        if (total <= 0f) {
            drawArc(emptyColor, 0f, FULL_CIRCLE, false, topLeft, arcSize, style = Stroke(stroke))
            return@Canvas
        }
        var start = START_AT_TOP
        slices.forEach { slice ->
            val sweep = slice.value / total * FULL_CIRCLE
            val gap = if (slices.size > 1) GAP_DEGREES else 0f
            drawArc(slice.color, start, (sweep - gap).coerceAtLeast(0f), false, topLeft, arcSize, style = Stroke(stroke))
            start += sweep
        }
    }
}
