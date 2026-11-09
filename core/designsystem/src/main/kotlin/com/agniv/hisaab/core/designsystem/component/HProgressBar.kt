package com.agniv.hisaab.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
    Box(
        modifier
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
