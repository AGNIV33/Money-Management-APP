package com.agniv.hisaab.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import com.agniv.hisaab.core.designsystem.component.categoryEmoji
import com.agniv.hisaab.core.domain.model.Category

internal fun Category.emoji(): String = categoryEmoji(icon)

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
