package com.agniv.hisaab.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.agniv.hisaab.core.designsystem.R

// Inter, cut to Latin + ₹ + a few symbols (4 × ~53 KB). Hindi has no Inter glyphs, so Android falls back to the
// phone's Noto Devanagari for those letters automatically.
val InterFamily =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

private fun style(
    size: Int,
    line: Int,
    weight: FontWeight,
) = TextStyle(fontFamily = InterFamily, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

// Line heights leave room for Devanagari, which sits taller than Latin.
internal val HisaabTypography =
    Typography(
        displaySmall = style(36, 44, FontWeight.Bold),
        headlineLarge = style(32, 40, FontWeight.Bold),
        headlineMedium = style(28, 36, FontWeight.Bold),
        headlineSmall = style(24, 32, FontWeight.Bold),
        titleLarge = style(20, 28, FontWeight.Bold),
        titleMedium = style(16, 24, FontWeight.SemiBold),
        titleSmall = style(14, 20, FontWeight.SemiBold),
        bodyLarge = style(16, 24, FontWeight.Normal),
        bodyMedium = style(14, 20, FontWeight.Normal),
        bodySmall = style(12, 16, FontWeight.Normal),
        labelLarge = style(14, 20, FontWeight.SemiBold),
        labelMedium = style(12, 16, FontWeight.Medium),
        labelSmall = style(11, 16, FontWeight.Medium),
    )

/** Big totals ("₹24,500"). "tnum" keeps digits the same width so a counting-up amount does not jiggle. */
val DisplayAmount =
    TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontFeatureSettings = "tnum",
    )

/** Amount inside cards ("₹12,500"). */
val CardAmount =
    TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontFeatureSettings = "tnum",
    )
