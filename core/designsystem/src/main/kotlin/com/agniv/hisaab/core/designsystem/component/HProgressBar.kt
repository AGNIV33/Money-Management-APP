package com.agniv.hisaab.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.R
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme

/** At or above this share of the limit the bar turns amber (DESIGN.md: 80% budget = warning). */
const val WARNING_FRACTION = 0.8f

/** Budget/goal/loan progress; colour = success / warning / error by how full it is. */
@Composable
fun HProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val color: Color =
        when {
            fraction >= 1f -> MaterialTheme.colorScheme.error
            fraction >= WARNING_FRACTION -> HisaabTheme.extendedColors.warning
            else -> HisaabTheme.extendedColors.success
        }
    // TalkBack reads "85 percent"; the colour alone means nothing to someone who cannot see it.
    val percent = progressPercent(fraction)
    val spoken = stringResource(R.string.ds_progress_percent, percent)
    Box(
        modifier
            .progressSemantics(fraction.coerceIn(0f, 1f))
            .semantics { stateDescription = spoken }
            .fillMaxWidth()
            .height(10.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(10.dp)
                .background(color, RoundedCornerShape(5.dp)),
        )
    }
}

/** Whole percent for TalkBack; over-budget shows the real share (e.g. 120), not 100. */
fun progressPercent(fraction: Float): Int = if (fraction.isNaN() || fraction < 0f) 0 else Math.round(fraction * PERCENT)

private const val PERCENT = 100f
