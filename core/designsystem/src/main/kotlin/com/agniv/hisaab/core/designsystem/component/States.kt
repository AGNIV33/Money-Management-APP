package com.agniv.hisaab.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.motion.popIn
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import kotlin.math.cos
import kotlin.math.sin

/**
 * Board illustration: a big gold outline icon with three short "spark" strokes above-right. Drawn, not an image,
 * so it costs no APK size and follows dark mode.
 */
@Composable
fun SparkIllustration(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    color: Color = HisaabTheme.extendedColors.gold,
) {
    Box(modifier.size(size).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val c = Offset(this.size.width * SPARK_X, this.size.height * SPARK_Y)
            val stroke = this.size.minDimension * STROKE_SHARE
            SPARK_ANGLES.forEach { deg ->
                val r = Math.toRadians(deg.toDouble())
                val dx = cos(r).toFloat()
                val dy = -sin(r).toFloat()
                val inner = this.size.minDimension * SPARK_INNER
                val outer = this.size.minDimension * SPARK_OUTER
                drawLine(
                    color = color,
                    start = Offset(c.x + dx * inner, c.y + dy * inner),
                    end = Offset(c.x + dx * outer, c.y + dy * outer),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * ICON_SHARE).popIn())
    }
}

@Suppress("MagicNumber") // Spark directions in degrees.
private val SPARK_ANGLES = listOf(20f, 55f, 90f)
private const val SPARK_X = 0.62f
private const val SPARK_Y = 0.42f
private const val SPARK_INNER = 0.36f
private const val SPARK_OUTER = 0.48f
private const val STROKE_SHARE = 0.035f
private const val ICON_SHARE = 0.62f

/** "No budgets yet" style screen: illustration, title, one line, optional button. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    actionIcon: ImageVector = Icons.Filled.Add,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        SparkIllustration(icon)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null && onAction != null) {
            HButton(onClick = onAction, modifier = Modifier.padding(top = Spacing.s)) {
                Icon(actionIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(action, modifier = Modifier.padding(start = Spacing.s))
            }
        }
    }
}

/** Red-tinted card: "Couldn't load your data." + Retry. */
@Composable
fun ErrorCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    retry: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    HCard(modifier = modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.errorContainer) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
            )
            if (retry != null && onRetry != null) {
                HButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(retry, modifier = Modifier.padding(start = Spacing.s))
                }
            }
        }
    }
}

/** One-line inline error under a field or keypad ("Incorrect PIN. Try again."). */
@Composable
fun InlineError(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}
