package com.agniv.hisaab.feature.expenses.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import com.agniv.hisaab.core.domain.model.Category

// Emoji for now: zero extra APK size and fast on low-end phones.
// TODO(Phase 5): swap for Material Symbols vectors (DESIGN.md §4).
private val iconEmoji =
    mapOf(
        "shopping_basket" to "🛒",
        "water_drop" to "🥛",
        "eco" to "🥦",
        "home" to "🏠",
        "bolt" to "💡",
        "water" to "🚰",
        "local_fire_department" to "🔥",
        "smartphone" to "📱",
        "cleaning_services" to "🧹",
        "local_gas_station" to "⛽",
        "directions_bus" to "🚌",
        "restaurant" to "🍽️",
        "shopping_bag" to "🛍️",
        "medical_services" to "💊",
        "school" to "🎓",
        "redeem" to "🎁",
        "account_balance" to "🏦",
        "movie" to "🎬",
        "category" to "📦",
    )

internal fun Category.emoji(): String = iconEmoji[icon] ?: "📦"

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
