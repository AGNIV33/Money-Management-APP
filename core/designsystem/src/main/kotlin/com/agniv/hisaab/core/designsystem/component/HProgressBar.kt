package com.agniv.hisaab.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.R
import com.agniv.hisaab.core.designsystem.motion.animateFill
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme

/** At or above this share of the limit the bar turns gold (boards: 80% = warning). */
const val WARNING_FRACTION = 0.8f

/** Which colour band a share of a limit falls in. Over = 100% or more. */
enum class Band { GOOD, WARN, OVER }

fun bandOf(fraction: Float): Band =
    when {
        fraction >= 1f -> Band.OVER
        fraction >= WARNING_FRACTION -> Band.WARN
        else -> Band.GOOD
    }

/**
 * Budget / goal / loan progress. Green under 80%, gold 80–99%, red at 100%+ (boards). Fills from empty the first
 * time it is shown. Pair it with a [PercentChip] or text: colour alone means nothing to someone who cannot see it.
 */
@Composable
fun HProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = bandColor(bandOf(fraction)),
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val shown by animateFill(fraction.coerceIn(0f, 1f))
    val spoken = stringResource(R.string.ds_progress_percent, progressPercent(fraction))
    Box(
        modifier
            .progressSemantics(fraction.coerceIn(0f, 1f))
            .semantics { stateDescription = spoken }
            .fillMaxWidth()
            .height(height)
            .background(trackColor, CircleShape),
    ) {
        Box(
            Modifier
                .fillMaxWidth(shown)
                .fillMaxHeight()
                .background(color, CircleShape),
        )
    }
}

@Composable
fun bandColor(band: Band): Color =
    when (band) {
        Band.GOOD -> MaterialTheme.colorScheme.primary
        Band.WARN -> HisaabTheme.extendedColors.gold
        Band.OVER -> HisaabTheme.extendedColors.over
    }

/** "80%" pill next to a bar; green / gold / red like the bar, with text that passes 4.5:1. */
@Composable
fun PercentChip(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val colors = HisaabTheme.extendedColors
    val (bg, fg) =
        when (bandOf(fraction)) {
            Band.GOOD -> colors.chipGood to colors.onChipGood
            Band.WARN -> colors.chipWarn to colors.onChipWarn
            Band.OVER -> colors.chipOver to colors.onChipOver
        }
    Text(
        text = "${progressPercent(fraction)}%",
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        // The bar beside it already speaks the percent.
        modifier =
            modifier
                .clearAndSetSemantics {}
                .background(bg, CircleShape)
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Whole percent for TalkBack; over-budget shows the real share (e.g. 120), not 100. */
fun progressPercent(fraction: Float): Int = if (fraction.isNaN() || fraction < 0f) 0 else Math.round(fraction * PERCENT)

private const val PERCENT = 100f
