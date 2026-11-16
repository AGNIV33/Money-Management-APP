package com.agniv.hisaab.lock

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.R
import com.agniv.hisaab.core.designsystem.motion.shake
import com.agniv.hisaab.core.designsystem.theme.Spacing

/**
 * Board PIN dots: filled green for entered digits, outlined for the rest; shakes on [shakeKey]. TalkBack hears
 * "2 of 4 digits entered" instead of four circles.
 */
@Composable
fun PinDots(
    entered: Int,
    shakeKey: Int,
    error: Boolean = false,
) {
    val spoken = stringResource(R.string.pin_entered, entered, PinAttempts.PIN_LENGTH)
    Row(
        Modifier
            .shake(shakeKey)
            .semantics {
                stateDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        repeat(PinAttempts.PIN_LENGTH) { i ->
            val filled = i < entered
            val ring = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            val fill by animateColorAsState(if (filled) ring else Color.Transparent, label = "dot")
            Box(
                Modifier
                    .size(18.dp)
                    .background(fill, CircleShape)
                    .border(2.dp, if (filled) ring else MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
    }
}

/**
 * Board number pad: 1–9, then fingerprint (when offered) / 0 / backspace. Keys are 64 dp circles, well over the
 * 48 dp minimum. [enabled] false during the 30-second wait.
 */
@Composable
fun PinPad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    enabled: Boolean,
    onFingerprint: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m), horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in (1..LAST_DIGIT).chunked(KEYS_PER_ROW)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                row.forEach { d ->
                    Key(enabled, label = "$d", onClick = { onDigit(d) }) { Text("$d", style = MaterialTheme.typography.headlineSmall) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            if (onFingerprint != null) {
                Key(enabled, label = stringResource(R.string.pin_use_fingerprint), onClick = onFingerprint, filled = true) {
                    Icon(
                        Icons.Filled.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            } else {
                Spacer(Modifier.size(KEY.dp))
            }
            Key(enabled, label = "0", onClick = { onDigit(0) }) { Text("0", style = MaterialTheme.typography.headlineSmall) }
            Key(enabled, label = stringResource(R.string.pin_backspace), onClick = onBackspace, outlined = false) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null)
            }
        }
    }
}

private const val KEY = 64
private const val LAST_DIGIT = 9
private const val KEYS_PER_ROW = 3

@Composable
private fun Key(
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
    filled: Boolean = false,
    outlined: Boolean = true,
    content: @Composable () -> Unit,
) {
    val bg =
        if (filled) {
            MaterialTheme.colorScheme.primary
        } else if (outlined) {
            MaterialTheme.colorScheme.surface
        } else {
            Color.Transparent
        }
    Box(
        Modifier
            .size(KEY.dp)
            .background(bg, CircleShape)
            .then(if (outlined && !filled) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}
