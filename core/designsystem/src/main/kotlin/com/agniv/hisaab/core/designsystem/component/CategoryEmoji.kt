package com.agniv.hisaab.core.designsystem.component

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

/** Emoji for a category icon name; one list for every screen. */
fun categoryEmoji(icon: String): String = iconEmoji[icon] ?: "📦"
