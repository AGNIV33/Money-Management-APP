package com.agniv.hisaab.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Plan 5.4: every colour the app uses for text must be readable on the surfaces it sits on.
 * WCAG AA asks for 4.5:1 for normal text.
 */
class ContrastTest {
    private fun ratio(
        a: Color,
        b: Color,
    ): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun assertReadable(
        name: String,
        text: Color,
        backgrounds: List<Color>,
    ) {
        backgrounds.forEach { bg ->
            assertWithMessage("$name on $bg").that(ratio(text, bg)).isAtLeast(AA_TEXT)
        }
    }

    @Test
    fun lightTextColoursPassOnSurfaceAndBackground() {
        val bgs = listOf(LightColors.surface, LightColors.background)
        assertReadable("onSurface", LightColors.onSurface, bgs)
        assertReadable("onSurfaceVariant", LightColors.onSurfaceVariant, bgs)
        assertReadable("primary", LightColors.primary, bgs)
        assertReadable("tertiary", LightColors.tertiary, bgs)
        assertReadable("error", LightColors.error, bgs)
        assertReadable("warning", LightExtendedColors.warning, bgs)
        assertReadable("success", LightExtendedColors.success, bgs)
    }

    @Test
    fun darkTextColoursPassOnSurfaceAndBackground() {
        val bgs = listOf(DarkColors.surface, DarkColors.background, DarkColors.surfaceContainerHighest)
        assertReadable("onSurface", DarkColors.onSurface, bgs)
        assertReadable("onSurfaceVariant", DarkColors.onSurfaceVariant, bgs)
        assertReadable("primary", DarkColors.primary, bgs)
        assertReadable("tertiary", DarkColors.tertiary, bgs)
        assertReadable("error", DarkColors.error, bgs)
        assertReadable("warning", DarkExtendedColors.warning, bgs)
        assertReadable("success", DarkExtendedColors.success, bgs)
    }

    @Test
    fun buttonAndChipLabelsPass() {
        assertReadable("onPrimary", LightColors.onPrimary, listOf(LightColors.primary))
        assertReadable("onPrimary dark", DarkColors.onPrimary, listOf(DarkColors.primary))
        assertReadable("onPrimaryContainer", LightColors.onPrimaryContainer, listOf(LightColors.primaryContainer))
        assertReadable("onPrimaryContainer dark", DarkColors.onPrimaryContainer, listOf(DarkColors.primaryContainer))
        assertReadable("onSecondaryContainer", LightColors.onSecondaryContainer, listOf(LightColors.secondaryContainer))
        assertReadable("onError", LightColors.onError, listOf(LightColors.error))
        assertReadable("onError dark", DarkColors.onError, listOf(DarkColors.error))
    }

    private companion object {
        const val AA_TEXT = 4.5
    }
}
