package com.agniv.hisaab.feature.tools

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.SectionHeader
import com.agniv.hisaab.core.designsystem.theme.Spacing
import java.util.Locale

internal enum class Tool(
    val icon: ImageVector,
    val title: Int,
    val hint: Int,
) {
    EMI(Icons.Outlined.Calculate, R.string.tool_emi, R.string.tool_emi_hint),
    PREPAY(Icons.Outlined.Savings, R.string.tool_prepay, R.string.tool_prepay_hint),
    AFFORD(Icons.Outlined.Balance, R.string.tool_afford, R.string.tool_afford_hint),
    SAVINGS(Icons.Outlined.Percent, R.string.tool_savings, R.string.tool_savings_hint),
    CALCULATOR(Icons.Outlined.Dialpad, R.string.tool_calculator, R.string.tool_calculator_hint),
}

/** The four big cards; the plain calculator sits under them as one wide row. */
private val GRID = listOf(Tool.EMI, Tool.PREPAY, Tool.AFFORD, Tool.SAVINGS)
private const val RECENT_SHOWN = 2

/** Tools (DESIGN.md §7.9): a tool opens in place, Back returns to the hub. */
@Composable
fun ToolsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: ToolsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf<Tool?>(null) }
    val tool = open
    if (tool == null) {
        ToolsHub(state, onBack, onOpen = { open = it })
        return
    }
    BackHandler { open = null }
    if (tool == Tool.CALCULATOR) {
        CalculatorTool(onBack = { open = null })
        return
    }
    // The last complete answer is saved to "Recently used" when the tool closes, not on every key press.
    val latest = remember(tool) { mutableStateOf<RecentResult?>(null) }
    DisposableEffect(tool) { onDispose { latest.value?.let(viewModel::saveRecent) } }
    val onResult: (RecentResult?) -> Unit = { latest.value = it }
    ToolPage(stringResource(tool.title), onBack = { open = null }) {
        when (tool) {
            Tool.EMI -> EmiTool(state, onResult)
            Tool.PREPAY -> PrepayTool(state, onResult)
            Tool.AFFORD -> AffordTool(state, onResult)
            Tool.SAVINGS -> SavingsTool(onResult)
            Tool.CALCULATOR -> Unit
        }
    }
}

@Composable
private fun ToolsHub(
    state: ToolsUiState,
    onBack: (() -> Unit)?,
    onOpen: (Tool) -> Unit,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val recent = state.recent.filter { r -> Tool.entries.any { it.name == r.tool } }
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item(key = "header") { HubHeader(onBack) }
        GRID.chunked(2).forEachIndexed { index, pair ->
            item(key = "row-$index") {
                // Same height for both cards in a row, whatever the text length.
                Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    pair.forEach { tool -> ToolCard(tool, Modifier.weight(1f).fillMaxHeight()) { onOpen(tool) } }
                }
            }
        }
        item(key = "calculator") { WideToolRow(Tool.CALCULATOR) { onOpen(Tool.CALCULATOR) } }
        if (recent.isNotEmpty()) {
            item(key = "recent-title") {
                SectionHeader(
                    title = stringResource(R.string.tools_recent),
                    modifier = Modifier.padding(top = Spacing.s),
                    action =
                        if (recent.size > RECENT_SHOWN) {
                            stringResource(if (showAll) R.string.tools_show_less else R.string.tools_see_all)
                        } else {
                            null
                        },
                    onAction = { showAll = !showAll },
                )
            }
            items(if (showAll) recent else recent.take(RECENT_SHOWN), key = { "recent-${it.tool}-${it.at}-${it.a}" }) { r ->
                RecentRow(r, onOpen)
            }
        }
        item(key = "tip") { TipCard(state.tip) }
        item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun HubHeader(onBack: (() -> Unit)?) {
    Box(Modifier.fillMaxWidth()) {
        // Soft green glow with a big calculator icon, in place of the mockup's 3D picture (cheap to draw).
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = Spacing.l)
                .size(HERO_SIZE)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Calculate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(HERO_ICON),
            )
        }
        Column(Modifier.padding(bottom = Spacing.l)) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.tools_back))
                }
            } else {
                Spacer(Modifier.height(Spacing.xxl))
            }
            Text(
                stringResource(R.string.tools_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = Spacing.s).semantics { heading() },
            )
            Text(
                stringResource(R.string.tools_subtitle),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val HERO_SIZE = 140.dp
private val HERO_ICON = 64.dp

@Composable
private fun ToolCard(
    tool: Tool,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    HCard(modifier.clip(MaterialTheme.shapes.medium).clickable(role = Role.Button, onClick = onClick)) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.Top) {
                ToolIcon(tool.icon, size = 52.dp)
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stringResource(tool.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(tool.hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WideToolRow(
    tool: Tool,
    onClick: () -> Unit,
) {
    HCard(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            ToolIcon(tool.icon, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(tool.title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(tool.hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentRow(
    r: RecentResult,
    onOpen: (Tool) -> Unit,
) {
    val tool = Tool.entries.firstOrNull { it.name == r.tool } ?: return
    HCard(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button) { onOpen(tool) },
    ) {
        Row(
            Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            ToolIcon(tool.icon, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(tool.title), style = MaterialTheme.typography.titleSmall)
                Text(
                    recentSummary(tool, r),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    DateUtils
                        .getRelativeTimeSpanString(r.at, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS)
                        .toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One line per tool, from the numbers saved in [RecentResult]. */
@Composable
private fun recentSummary(
    tool: Tool,
    r: RecentResult,
): String =
    when (tool) {
        Tool.EMI -> stringResource(R.string.recent_emi, Money(r.a).formatted(), tenureText(r.b.toInt()), Money(r.c).formatted())
        Tool.PREPAY -> stringResource(R.string.recent_prepay, Money(r.a).formatted(), Money(r.b).formatted())
        Tool.AFFORD ->
            stringResource(R.string.recent_afford, Money(r.a).formatted(), String.format(Locale.US, "%.1f", r.b / TENTHS))
        Tool.SAVINGS -> stringResource(R.string.recent_savings, Money(r.a).formatted(), tenureText(r.b.toInt()))
        Tool.CALCULATOR -> ""
    }

private const val TENTHS = 10.0
private const val MONTHS_PER_YEAR = 12

@Composable
private fun tenureText(months: Int): String =
    if (months % MONTHS_PER_YEAR == 0) {
        stringResource(R.string.tenure_years, months / MONTHS_PER_YEAR)
    } else {
        stringResource(R.string.tenure_months, months)
    }

/** Uses the family's own loan when there is one, so the numbers are real; otherwise a general tip. */
@Composable
private fun TipCard(tip: PrepayTip?) {
    HCard(
        Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            Modifier.padding(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.size(36.dp))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.tip_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (tip != null) {
                    Text(
                        stringResource(R.string.tip_prepay, tip.lump.formatted(), tip.interestSaved.formatted()),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (tip.monthsSaved > 0) {
                        Text(stringResource(R.string.tip_prepay_months, tip.monthsSaved), style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(stringResource(R.string.tip_general), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
