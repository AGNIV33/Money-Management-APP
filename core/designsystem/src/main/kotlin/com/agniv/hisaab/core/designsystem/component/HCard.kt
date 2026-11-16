package com.agniv.hisaab.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.theme.ButtonShape
import com.agniv.hisaab.core.designsystem.theme.Spacing

/**
 * Kinly card: surface colour, 1 dp soft border, 20 dp corners and no shadow. A flat border is cheaper to draw than
 * elevation on low-end phones and matches the boards. Tinted cards drop the border.
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
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = MIN_BUTTON_HEIGHT.dp),
        enabled = enabled,
        shape = ButtonShape,
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.m),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        content = content,
    )
}

/** Primary button that shows a spinner and [busyText] ("Saving...") while [busy]. */
@Composable
fun HBusyButton(
    text: String,
    busyText: String,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    HButton(onClick = onClick, modifier = modifier, enabled = enabled && !busy) {
        if (busy) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
                modifier = Modifier.size(18.dp),
            )
            Text(busyText, modifier = Modifier.padding(start = Spacing.s))
        } else {
            Text(text)
        }
    }
}

/** Secondary action ("Cancel", "Retake"): outlined, same shape and height as [HButton]. */
@Composable
fun HOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MIN_BUTTON_HEIGHT.dp),
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.m),
        content = content,
    )
}

private const val MIN_BUTTON_HEIGHT = 52
