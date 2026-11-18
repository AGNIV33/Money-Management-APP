package com.agniv.hisaab.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.theme.Spacing

/**
 * Board pill tabs ("I gave | I owe", "English | हिंदी"): the chosen one is solid green. A radio group for TalkBack.
 * The colour slides between pills (150 ms) instead of jumping.
 */
@Composable
fun PillTabs(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
) {
    Row(
        modifier
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, label = "pill")
            Box(
                (if (fill) Modifier.weight(1f) else Modifier)
                    .heightIn(min = 44.dp)
                    .background(bg, CircleShape)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(i) })
                    .padding(horizontal = Spacing.l),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Round initial for a person ("R" for Rohit) in a soft colour picked from the name, so it stays the same. */
@Composable
fun InitialAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val tint = AvatarColors[(name.hashCode() and Int.MAX_VALUE) % AvatarColors.size]
    Box(
        modifier
            .size(size)
            .background(tint.copy(alpha = AVATAR_ALPHA), CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = tint,
        )
    }
}

// Dark enough that the letter on its own soft circle reads at 4.5:1.
@Suppress("MagicNumber") // Colour values.
private val AvatarColors =
    listOf(
        Color(0xFFB3261E),
        Color(0xFF1B6F8A),
        Color(0xFF8A5A00),
        Color(0xFF6A3FA0),
        Color(0xFF1B7F5B),
        Color(0xFFA0336A),
    )
private const val AVATAR_ALPHA = 0.14f
