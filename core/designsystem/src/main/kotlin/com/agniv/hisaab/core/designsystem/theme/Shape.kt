package com.agniv.hisaab.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// DESIGN.md §4 (Kinly spec): small controls 10, cards 20, large panels and sheets 28.
internal val HisaabShapes =
    Shapes(
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
    )

/** Primary buttons: 12 dp corners per the spec, instead of Material's full pill. */
val ButtonShape = RoundedCornerShape(12.dp)

/** Spacing scale from DESIGN.md §4. Use these instead of raw dp values. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screen = l
}
