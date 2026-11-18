package com.agniv.hisaab.feature.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.theme.Spacing

/**
 * Every tool page: back arrow, big title + subtitle with the green glow picture, then a scrolling column. Draws its own
 * background so the page never shows the window colour behind it (that was why titles were black on black).
 */
@Composable
internal fun ToolPage(
    title: String,
    subtitle: String?,
    art: ImageVector,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xl)
                .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        PageHeader(title, subtitle, art, onBack)
        content()
    }
}

/** Header shared by the hub and the tools. The picture is a cheap gradient + icon, not an image file. */
@Composable
internal fun PageHeader(
    title: String,
    subtitle: String?,
    art: ImageVector,
    onBack: (() -> Unit)?,
) {
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = Spacing.s)
                .size(ART_SIZE)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primaryContainer, Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(art, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(ART_ICON))
        }
        Column(Modifier.padding(end = ART_SIZE / 2)) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.tools_back))
                }
            } else {
                Spacer(Modifier.height(Spacing.xl))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val ART_SIZE = 120.dp
private val ART_ICON = 56.dp

/** All inputs of a tool in one card; rows are split by [RowDivider]. */
@Composable
internal fun InputCard(content: @Composable ColumnScope.() -> Unit) {
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s), content = content)
    }
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** The answer card: brand tint with a primary border, as on the boards. Text uses onPrimaryContainer. */
@Composable
internal fun ResultCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = BORDER_ALPHA)),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m), content = content)
    }
}

private const val BORDER_ALPHA = 0.6f

/** Plain card for details under the result. */
@Composable
internal fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m), content = content)
    }
}

/** Label on the left, value on the right; optional colour dot or icon in front. Uses the card's content colour. */
@Composable
internal fun Stat(
    label: String,
    value: String,
    dot: Color? = null,
    icon: ImageVector? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (dot != null) Box(Modifier.size(DOT_SIZE).clip(CircleShape).background(dot))
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(ICON_SMALL))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

private val DOT_SIZE = 10.dp
private val ICON_SMALL = 20.dp

/** Shown instead of a grey hint line while the inputs are not complete. */
@Composable
internal fun EmptyResult(
    text: String,
    icon: ImageVector = Icons.Outlined.Calculate,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ToolIcon(icon, size = EMPTY_ICON)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val EMPTY_ICON = 64.dp

/** Outlined icon in a soft green circle. Decorative: the text next to it is read instead. */
@Composable
internal fun ToolIcon(
    icon: ImageVector,
    size: Dp = 48.dp,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size / 2))
    }
}

/** Outlined box inside a card for tips and advice: icon + text. */
@Composable
internal fun TipBox(
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, TIP_SHAPE)
            .padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(TIP_ICON))
        Column(Modifier.weight(1f), content = content)
    }
}

private val TIP_SHAPE = RoundedCornerShape(12.dp)
private val TIP_ICON = 28.dp
