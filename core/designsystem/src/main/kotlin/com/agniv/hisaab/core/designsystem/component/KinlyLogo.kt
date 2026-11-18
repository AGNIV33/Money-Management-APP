package com.agniv.hisaab.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.theme.Spacing

/**
 * The boards' Kinly mark: a green house with the family inside, then the word "Kinly". Drawn from two vector icons,
 * so it costs nothing and follows dark mode. TalkBack hears just "Kinly".
 */
@Composable
fun KinlyLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    textStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    showName: Boolean = true,
) {
    Row(
        modifier.clearAndSetSemantics { contentDescription = "Kinly" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size))
            Icon(
                Icons.Filled.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier =
                    Modifier
                        .size(size * PEOPLE)
                        .offset(y = size * PEOPLE_DROP),
            )
        }
        if (showName) Text("Kinly", style = textStyle, color = MaterialTheme.colorScheme.primary)
    }
}

private const val PEOPLE = 0.42f
private const val PEOPLE_DROP = 0.12f
