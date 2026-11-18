package com.agniv.hisaab.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

// Category icon names are Material Symbol names stored with each category, so the boards' outline icons map 1:1.
private val icons: Map<String, ImageVector> =
    mapOf(
        "shopping_basket" to Icons.Outlined.ShoppingBasket,
        "water_drop" to Icons.Outlined.WaterDrop,
        "eco" to Icons.Outlined.Eco,
        "home" to Icons.Outlined.Home,
        "bolt" to Icons.Outlined.Bolt,
        "water" to Icons.Outlined.WaterDrop,
        "local_fire_department" to Icons.Outlined.LocalFireDepartment,
        "smartphone" to Icons.Outlined.Smartphone,
        "cleaning_services" to Icons.Outlined.CleaningServices,
        "local_gas_station" to Icons.Outlined.LocalGasStation,
        "directions_bus" to Icons.Outlined.DirectionsBus,
        "restaurant" to Icons.Outlined.Restaurant,
        "shopping_bag" to Icons.Outlined.ShoppingBag,
        "medical_services" to Icons.Outlined.MedicalServices,
        "school" to Icons.Outlined.School,
        "redeem" to Icons.Outlined.Redeem,
        "account_balance" to Icons.Outlined.AccountBalance,
        "movie" to Icons.Outlined.Movie,
        "category" to Icons.Outlined.Category,
    )

/** Outline icon for a category's stored icon name; one list for every screen. */
fun categoryIcon(icon: String): ImageVector = icons[icon] ?: Icons.Outlined.Category
