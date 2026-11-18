package com.agniv.hisaab.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Kinly design boards (Nov 2026). Code is the source of truth; DESIGN.md §2 points here.
// Every text/background pair below was checked for WCAG AA (4.5:1); where a board colour failed, the same hue is a
// little darker (light theme) or lighter (dark theme).
internal object Palette {
    // Light
    val Primary = Color(0xFF1B7F5B)
    val PrimaryDeep = Color(0xFF125C42)
    val PrimaryTint = Color(0xFFE6F3EC)
    val Gold = Color(0xFFE9B872)
    val Background = Color(0xFFFAFAF6)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF17251F)
    val InkSoft = Color(0xFF68766F)
    val Border = Color(0xFFE5EAE6)
    val FieldBorder = Color(0xFF7D8983)
    val Danger = Color(0xFFB84040)
    val Warning = Color(0xFF8F5E12)
    val Success = Color(0xFF1E7A4B)
    val Blue = Color(0xFF3461C4)

    // Dark: deep green-black canvas from the boards (#111A16 / #1B2721). Primary is a light green with dark text on
    // buttons: the boards' white-on-green buttons are only 3.4:1.
    val DPrimary = Color(0xFF5FD39F)
    val DOnPrimary = Color(0xFF06301E)
    val DPrimaryContainer = Color(0xFF0F5F44)
    val DBackground = Color(0xFF111A16)
    val DSurface = Color(0xFF1B2721)
    val DInk = Color(0xFFE6EEE9)
    val DInkSoft = Color(0xFFA9B8B0)
    val DBorder = Color(0xFF2C3A33)
    val DFieldBorder = Color(0xFF6E7F76)
    val DDanger = Color(0xFFF08A8A)
    val DWarning = Color(0xFFF2C26B)
    val DSuccess = Color(0xFF7FDDB0)
    val DBlue = Color(0xFFA9C2FF)
}

internal val LightColors =
    lightColorScheme(
        primary = Palette.Primary,
        onPrimary = Color.White,
        primaryContainer = Palette.PrimaryTint,
        onPrimaryContainer = Palette.PrimaryDeep,
        secondary = Palette.Gold,
        onSecondary = Palette.Ink,
        secondaryContainer = Palette.PrimaryTint,
        onSecondaryContainer = Palette.PrimaryDeep,
        tertiary = Palette.Blue,
        onTertiary = Color.White,
        error = Palette.Danger,
        onError = Color.White,
        errorContainer = Color(0xFFFDE8E8),
        onErrorContainer = Color(0xFF8A2A2A),
        background = Palette.Background,
        onBackground = Palette.Ink,
        surface = Palette.Surface,
        onSurface = Palette.Ink,
        surfaceVariant = Color(0xFFEEF2EF),
        onSurfaceVariant = Palette.InkSoft,
        outline = Palette.FieldBorder,
        outlineVariant = Palette.Border,
        surfaceContainerLowest = Palette.Surface,
        surfaceContainerLow = Palette.Surface,
        surfaceContainer = Color(0xFFF3F5F2),
        surfaceContainerHigh = Color(0xFFEBEFEC),
        surfaceContainerHighest = Color(0xFFE5E9E6),
    )

internal val DarkColors =
    darkColorScheme(
        primary = Palette.DPrimary,
        onPrimary = Palette.DOnPrimary,
        primaryContainer = Palette.DPrimaryContainer,
        onPrimaryContainer = Color.White,
        secondary = Palette.Gold,
        onSecondary = Palette.Ink,
        secondaryContainer = Palette.DPrimaryContainer,
        onSecondaryContainer = Color.White,
        tertiary = Palette.DBlue,
        onTertiary = Color(0xFF002E6B),
        error = Palette.DDanger,
        onError = Color(0xFF5A1010),
        errorContainer = Color(0xFF3A1F1F),
        onErrorContainer = Color(0xFFF5C2C2),
        background = Palette.DBackground,
        onBackground = Palette.DInk,
        surface = Palette.DSurface,
        onSurface = Palette.DInk,
        surfaceVariant = Color(0xFF26342D),
        onSurfaceVariant = Palette.DInkSoft,
        outline = Palette.DFieldBorder,
        outlineVariant = Palette.DBorder,
        surfaceContainerLowest = Palette.DBackground,
        surfaceContainerLow = Palette.DSurface,
        surfaceContainer = Color(0xFF1F2C26),
        surfaceContainerHigh = Color(0xFF26342D),
        surfaceContainerHighest = Color(0xFF2D3C35),
    )

/** Colours Material 3 has no slot for. Pairs (`x` / `onX`) are contrast-checked together. */
@Immutable
data class ExtendedColors(
    val warning: Color,
    val success: Color,
    // Home "This Month's Spend" card: solid green with white text.
    val hero: Color,
    val onHero: Color,
    // Gold accent: progress at 80–99%, savings jars, illustrations. Never text on light surfaces.
    val gold: Color,
    // Soft cream card behind tips, insights and "Scan bill" (board: #FFF7E8).
    val goldContainer: Color,
    val onGoldContainer: Color,
    // Over-limit red for bars (non-text).
    val over: Color,
    // Percent pills: green / gold / red.
    val chipGood: Color,
    val onChipGood: Color,
    val chipWarn: Color,
    val onChipWarn: Color,
    val chipOver: Color,
    val onChipOver: Color,
)

internal val LightExtendedColors =
    ExtendedColors(
        warning = Palette.Warning,
        success = Palette.Success,
        hero = Palette.Primary,
        onHero = Color.White,
        gold = Palette.Gold,
        goldContainer = Color(0xFFFFF7E8),
        onGoldContainer = Color(0xFF6B4A12),
        over = Color(0xFFD32F2F),
        chipGood = Palette.PrimaryTint,
        onChipGood = Palette.PrimaryDeep,
        chipWarn = Color(0xFFFBEBD0),
        onChipWarn = Color(0xFF7A4E0E),
        chipOver = Color(0xFFFBE3E3),
        onChipOver = Color(0xFF9E2B2B),
    )

internal val DarkExtendedColors =
    ExtendedColors(
        warning = Palette.DWarning,
        success = Palette.DSuccess,
        hero = Palette.DPrimaryContainer,
        onHero = Color.White,
        gold = Palette.Gold,
        goldContainer = Color(0xFF332A18),
        onGoldContainer = Color(0xFFF2D49B),
        over = Color(0xFFF07070),
        chipGood = Color(0xFF1F4A36),
        onChipGood = Color(0xFF9FE6C2),
        chipWarn = Color(0xFF4A3A1C),
        onChipWarn = Color(0xFFF2C26B),
        chipOver = Color(0xFF4A2323),
        onChipOver = Color(0xFFF5A3A3),
    )
