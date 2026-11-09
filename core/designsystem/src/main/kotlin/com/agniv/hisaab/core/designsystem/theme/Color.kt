package com.agniv.hisaab.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Tokens from DESIGN.md §2. Keep both tables in sync.
internal object Palette {
    val PrimaryLight = Color(0xFF1B7F5C)
    val PrimaryDark = Color(0xFF6FD6AE)
    val OnPrimaryLight = Color(0xFFFFFFFF)
    val OnPrimaryDark = Color(0xFF00382A)
    val PrimaryContainerLight = Color(0xFFC9F2E0)
    val PrimaryContainerDark = Color(0xFF005139)
    val SecondaryLight = Color(0xFFF2A93B)
    val SecondaryDark = Color(0xFFFFC877)
    val TertiaryLight = Color(0xFF3E6FD8)
    val TertiaryDark = Color(0xFFA9C2FF)
    val ErrorLight = Color(0xFFC62828)
    val ErrorDark = Color(0xFFFFB4AB)
    val WarningLight = Color(0xFFE68A00)
    val WarningDark = Color(0xFFFFB95C)
    val SuccessLight = Color(0xFF2E7D32)
    val SuccessDark = Color(0xFF81C784)
    val BackgroundLight = Color(0xFFF7F8F6)
    val BackgroundDark = Color(0xFF101413)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceDark = Color(0xFF181D1B)
    val OnSurfaceLight = Color(0xFF1A1C1B)
    val OnSurfaceDark = Color(0xFFE1E3E0)
    val OnSurfaceVariantLight = Color(0xFF5D635F)
    val OnSurfaceVariantDark = Color(0xFFBFC9C3)
    val OutlineLight = Color(0xFFD5DBD7)
    val OutlineDark = Color(0xFF3A413D)
}

internal val LightColors =
    lightColorScheme(
        primary = Palette.PrimaryLight,
        onPrimary = Palette.OnPrimaryLight,
        primaryContainer = Palette.PrimaryContainerLight,
        onPrimaryContainer = Color(0xFF002116),
        secondary = Palette.SecondaryLight,
        onSecondary = Color(0xFF2B1700),
        tertiary = Palette.TertiaryLight,
        onTertiary = Color(0xFFFFFFFF),
        error = Palette.ErrorLight,
        onError = Color(0xFFFFFFFF),
        background = Palette.BackgroundLight,
        onBackground = Palette.OnSurfaceLight,
        surface = Palette.SurfaceLight,
        onSurface = Palette.OnSurfaceLight,
        surfaceVariant = Color(0xFFEDF1EE),
        onSurfaceVariant = Palette.OnSurfaceVariantLight,
        outline = Palette.OutlineLight,
        outlineVariant = Palette.OutlineLight,
        // Without these Material falls back to its purple baseline for chips, keypad and sheets.
        secondaryContainer = Palette.PrimaryContainerLight,
        onSecondaryContainer = Color(0xFF002116),
        surfaceContainerLowest = Palette.SurfaceLight,
        surfaceContainerLow = Palette.SurfaceLight,
        surfaceContainer = Color(0xFFF1F4F2),
        surfaceContainerHigh = Color(0xFFEBEFEC),
        surfaceContainerHighest = Color(0xFFE5E9E6),
    )

internal val DarkColors =
    darkColorScheme(
        primary = Palette.PrimaryDark,
        onPrimary = Palette.OnPrimaryDark,
        primaryContainer = Palette.PrimaryContainerDark,
        onPrimaryContainer = Color(0xFFC9F2E0),
        secondary = Palette.SecondaryDark,
        onSecondary = Color(0xFF452B00),
        tertiary = Palette.TertiaryDark,
        onTertiary = Color(0xFF002E6B),
        error = Palette.ErrorDark,
        onError = Color(0xFF690005),
        background = Palette.BackgroundDark,
        onBackground = Palette.OnSurfaceDark,
        surface = Palette.SurfaceDark,
        onSurface = Palette.OnSurfaceDark,
        surfaceVariant = Color(0xFF232A27),
        onSurfaceVariant = Palette.OnSurfaceVariantDark,
        outline = Palette.OutlineDark,
        outlineVariant = Palette.OutlineDark,
        secondaryContainer = Palette.PrimaryContainerDark,
        onSecondaryContainer = Color(0xFFC9F2E0),
        surfaceContainerLowest = Palette.BackgroundDark,
        surfaceContainerLow = Palette.SurfaceDark,
        surfaceContainer = Color(0xFF1D2321),
        surfaceContainerHigh = Color(0xFF232A27),
        surfaceContainerHighest = Color(0xFF2A322F),
    )

/** Colors Material 3 has no slot for (DESIGN.md "custom" tokens). */
@Immutable
data class ExtendedColors(
    val warning: Color,
    val success: Color,
)

internal val LightExtendedColors = ExtendedColors(warning = Palette.WarningLight, success = Palette.SuccessLight)
internal val DarkExtendedColors = ExtendedColors(warning = Palette.WarningDark, success = Palette.SuccessDark)
