package com.agniv.hisaab.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.theme.ButtonShape
import com.agniv.hisaab.core.designsystem.theme.Spacing

/**
 * Kinly card: white, 1 dp soft border, 20 dp corners and no shadow. A flat border is cheaper to draw than
 * elevation on low-end phones and matches the spec. Tinted cards (e.g. the month total) drop the border.
 */
@Composable
fun HCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bordered = containerColor == MaterialTheme.colorScheme.surface
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (bordered) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        content = content,
    )
}

/** Kinly primary button: 12 dp corners and at least 52 dp tall, so it is easy to hit. */
@Composable
fun HButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = MIN_BUTTON_HEIGHT.dp),
        enabled = enabled,
        shape = ButtonShape,
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.m),
        colors = ButtonDefaults.buttonColors(),
        content = content,
    )
}

private const val MIN_BUTTON_HEIGHT = 52
