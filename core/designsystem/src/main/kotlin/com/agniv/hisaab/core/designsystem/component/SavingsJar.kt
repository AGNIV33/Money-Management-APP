package com.agniv.hisaab.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.motion.animateFill
import com.agniv.hisaab.core.designsystem.motion.popIn
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme

/**
 * Board savings jar: a jar outline that fills from the bottom (green under 80%, gold 80–99%, full green with a check
 * when reached) with the percent inside. Drawn on one Canvas, so it is light; the fill rises on first show.
 * Decorative for TalkBack: the card next to it says the amounts in words.
 */
@Composable
fun SavingsJar(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val reached = fraction >= 1f
    val fill =
        when {
            reached -> MaterialTheme.colorScheme.primary
            fraction >= WARNING_FRACTION -> HisaabTheme.extendedColors.gold
            else -> MaterialTheme.colorScheme.primary.copy(alpha = FILL_ALPHA)
        }
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val shown by animateFill(fraction.coerceIn(0f, 1f))
    Box(modifier.size(size).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val stroke = w * STROKE
            val lidH = h * LID_H
            val bodyTop = lidH + stroke
            val body = Size(w * BODY_W, h - bodyTop - stroke)
            val bodyLeft = (w - body.width) / 2
            val radius = CornerRadius(w * BODY_R)
            val jar =
                Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = bodyLeft,
                            top = bodyTop,
                            right = bodyLeft + body.width,
                            bottom = bodyTop + body.height,
                            cornerRadius = radius,
                        ),
                    )
                }
            clipPath(jar) {
                val fillTop = bodyTop + body.height * (1f - shown)
                drawRect(fill, topLeft = Offset(bodyLeft, fillTop), size = Size(body.width, body.height * shown))
            }
            drawPath(jar, outline, style = Stroke(stroke))
            val lidW = w * LID_W
            drawRoundRect(
                color = outline,
                topLeft = Offset((w - lidW) / 2, stroke / 2),
                size = Size(lidW, lidH),
                cornerRadius = CornerRadius(lidH / 2),
                style = Stroke(stroke),
            )
        }
        if (reached) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * ICON).popIn())
        } else {
            Text(
                "${progressPercent(fraction)}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.popIn(),
            )
        }
    }
}

private const val STROKE = 0.05f
private const val LID_H = 0.16f
private const val LID_W = 0.62f
private const val BODY_W = 0.86f
private const val BODY_R = 0.18f
private const val FILL_ALPHA = 0.55f
private const val ICON = 0.45f
