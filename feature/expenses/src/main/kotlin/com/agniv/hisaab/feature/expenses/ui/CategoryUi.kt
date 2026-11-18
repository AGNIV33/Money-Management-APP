package com.agniv.hisaab.feature.expenses.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.component.IconTile
import com.agniv.hisaab.core.designsystem.component.categoryEmoji
import com.agniv.hisaab.core.designsystem.component.categoryIcon
import com.agniv.hisaab.core.domain.model.Category

internal fun Category.emoji(): String = categoryEmoji(icon)

internal fun Category.iconVector(): ImageVector = categoryIcon(icon)

/** Category icon in a soft square of the category's own colour (board "Recent Expenses" rows). */
@Composable
internal fun CategoryTile(
    category: Category?,
    size: Dp = 44.dp,
) {
    val tint = category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary
    IconTile(
        icon = category?.iconVector() ?: categoryIcon(""),
        tint = tint,
        container = tint.copy(alpha = TILE_ALPHA),
        size = size,
    )
}

private const val TILE_ALPHA = 0.14f

/** Hindi name when the app is in Hindi and one exists. */
@Composable
@ReadOnlyComposable
internal fun Category.label(): String {
    val language =
        LocalConfiguration.current.locales
            .get(0)
            .language
    return if (language == "hi" && !nameHi.isNullOrBlank()) nameHi.orEmpty() else name
}
