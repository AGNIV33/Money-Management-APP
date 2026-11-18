package com.agniv.hisaab.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.theme.Spacing

/**
 * Every tool page: top bar with back arrow, then a scrolling column. Draws its own background so the page never
 * shows the window colour behind it (that was why titles were black on black in dark mode).
 */
@Composable
internal fun ToolPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        KinlyTopBar(title = title, onBack = onBack)
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen)
                    .padding(bottom = Spacing.xl)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
            content = content,
        )
    }
}

/** All inputs of a tool sit in one soft card. */
@Composable
internal fun InputCard(content: @Composable ColumnScope.() -> Unit) {
    HCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m), content = content)
    }
}

/** The answer, big, on the brand-tint card. Shows live as the user types. */
@Composable
internal fun ResultHero(
    label: String,
    value: String,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    HCard(
        Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.headlineLarge, maxLines = 1)
            content()
        }
    }
}

/** Plain card for details under the hero. */
@Composable
internal fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m), content = content)
    }
}

@Composable
internal fun Stat(
    label: String,
    value: String,
    dot: Color? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (dot != null) Box(Modifier.size(DOT_SIZE).clip(CircleShape).background(dot))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

private val DOT_SIZE = 10.dp

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

/** Outlined icon in a soft green circle, as in the Tools design. Decorative: the text next to it is read instead. */
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
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(size / 2),
        )
    }
}

/** Two bars to compare before and after; widths are shares of the larger one. */
@Composable
internal fun CompareBars(
    before: Pair<String, Money>,
    after: Pair<String, Money>,
    description: String,
) {
    val max = maxOf(before.second.paise, after.second.paise).coerceAtLeast(1)
    Column(
        Modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Bar(before.first, before.second, before.second.paise.toFloat() / max, MaterialTheme.colorScheme.outline)
        Bar(after.first, after.second, after.second.paise.toFloat() / max, MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Bar(
    label: String,
    amount: Money,
    share: Float,
    color: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(amount.formatted(), style = MaterialTheme.typography.labelLarge)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .clip(BAR_SHAPE)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(share.coerceIn(MIN_BAR, 1f))
                    .height(BAR_HEIGHT)
                    .clip(BAR_SHAPE)
                    .background(color),
            )
        }
    }
}

private val BAR_HEIGHT = 12.dp
private val BAR_SHAPE = RoundedCornerShape(6.dp)
private const val MIN_BAR = 0.02f
