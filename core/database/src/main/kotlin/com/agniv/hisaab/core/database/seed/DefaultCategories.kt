package com.agniv.hisaab.core.database.seed

import com.agniv.hisaab.core.database.entity.CategoryEntity

/**
 * Built-in categories (PRD F4). IDs are fixed so every phone and Supabase agree on them;
 * the same rows are seeded on the server by supabase/migrations/003_default_categories.sql.
 * `icon` is a key the UI maps to a Material icon.
 */
object DefaultCategories {
    const val GROCERIES = "a156efbe-b53f-3019-811f-30c2d8567361"
    const val MILK = "d19126f3-f337-30a4-8d9a-d09cb866b1ce"
    const val VEGETABLES = "b2311670-4075-38d8-b479-d50ba8072937"
    const val RENT = "09d95305-98fa-3840-bed6-ca31c563d4a2"
    const val ELECTRICITY = "4e17013d-9ff1-3369-b3fe-08fd5ed751be"
    const val WATER = "5545c8b3-ccf1-37e8-80ee-89f7d6cdf84a"
    const val GAS = "1d1dce8a-b25a-3aaa-b2a5-20284a162bca"
    const val MOBILE = "70491a9d-d1ba-3792-9c4b-176615b61a10"
    const val HELP = "2dda8171-5e37-3801-b92a-a291f563b1d4"
    const val FUEL = "bd611778-3664-329d-90ac-2850ffbd2421"
    const val TRANSPORT = "3088bf49-2684-3ae2-ac1d-748e19fc809a"
    const val FOOD_OUTSIDE = "31586a39-badd-3178-8dc2-0739c0a9061f"
    const val SHOPPING = "272f87e8-507e-3efd-b5c6-ff986778cecc"
    const val MEDICAL = "e3949412-5e91-3a6f-94aa-8bafee967587"
    const val EDUCATION = "ba5f51b2-e0ff-3e2a-b5d6-66cd89e40a2b"
    const val FESTIVALS = "6a6ba212-0cc2-3d67-98e2-e8a6dc2dc39d"
    const val EMI = "d494f1d1-1f6a-3fbc-9cd0-b9ac0dadfdf2"
    const val ENTERTAINMENT = "922cec38-9309-33de-a7f9-eded83611a8d"
    const val OTHERS = "752c4d87-7774-3f88-a95a-8287d619fb7e"

    fun all(updatedAt: Long): List<CategoryEntity> =
        listOf(
            entity(GROCERIES, "Groceries", "किराना", "shopping_basket", 0xFF2E7D32, 0, updatedAt),
            entity(MILK, "Milk", "दूध", "water_drop", 0xFF5C6BC0, 1, updatedAt),
            entity(VEGETABLES, "Vegetables", "सब्ज़ी", "eco", 0xFF558B2F, 2, updatedAt),
            entity(RENT, "Rent", "किराया", "home", 0xFF6D4C41, 3, updatedAt),
            entity(ELECTRICITY, "Electricity", "बिजली", "bolt", 0xFFF9A825, 4, updatedAt),
            entity(WATER, "Water", "पानी", "water", 0xFF0288D1, 5, updatedAt),
            entity(GAS, "Gas/LPG", "गैस", "local_fire_department", 0xFFE65100, 6, updatedAt),
            entity(MOBILE, "Mobile/Internet", "मोबाइल/इंटरनेट", "smartphone", 0xFF00897B, 7, updatedAt),
            entity(HELP, "Maid/Help", "घरेलू सहायक", "cleaning_services", 0xFF8D6E63, 8, updatedAt),
            entity(FUEL, "Fuel", "पेट्रोल/डीज़ल", "local_gas_station", 0xFFC62828, 9, updatedAt),
            entity(TRANSPORT, "Transport", "आना-जाना", "directions_bus", 0xFF3949AB, 10, updatedAt),
            entity(FOOD_OUTSIDE, "Food Outside", "बाहर खाना", "restaurant", 0xFFD84315, 11, updatedAt),
            entity(SHOPPING, "Shopping", "शॉपिंग", "shopping_bag", 0xFFAD1457, 12, updatedAt),
            entity(MEDICAL, "Medical", "दवा/इलाज", "medical_services", 0xFFD32F2F, 13, updatedAt),
            entity(EDUCATION, "Education", "पढ़ाई", "school", 0xFF1565C0, 14, updatedAt),
            entity(FESTIVALS, "Festivals/Gifts", "त्योहार/उपहार", "redeem", 0xFFEF6C00, 15, updatedAt),
            entity(EMI, "EMI", "ईएमआई", "account_balance", 0xFF3E6FD8, 16, updatedAt),
            entity(ENTERTAINMENT, "Entertainment", "मनोरंजन", "movie", 0xFF7B1FA2, 17, updatedAt),
            entity(OTHERS, "Others", "अन्य", "category", 0xFF546E7A, 18, updatedAt),
        )

    @Suppress("LongParameterList")
    private fun entity(
        id: String,
        name: String,
        nameHi: String,
        icon: String,
        color: Long,
        order: Int,
        updatedAt: Long,
    ) = CategoryEntity(
        id = id,
        familyId = null,
        name = name,
        nameHi = nameHi,
        icon = icon,
        color = color,
        isDefault = true,
        sortOrder = order,
        updatedAt = updatedAt,
    )
}
