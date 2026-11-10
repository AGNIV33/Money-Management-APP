package com.agniv.hisaab.feature.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.theme.Spacing

internal enum class Tool(
    val emoji: String,
    val title: Int,
    val hint: Int,
) {
    EMI("🧮", R.string.tool_emi, R.string.tool_emi_hint),
    PREPAY("💰", R.string.tool_prepay, R.string.tool_prepay_hint),
    AFFORD("⚖️", R.string.tool_afford, R.string.tool_afford_hint),
    CALCULATOR("➗", R.string.tool_calculator, R.string.tool_calculator_hint),
}

/** Tools tab (DESIGN.md §7.9): four cards; a tool opens in place, Back returns to the cards. */
@Composable
fun ToolsScreen(viewModel: ToolsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf<Tool?>(null) }
    val tool = open
    if (tool == null) {
        ToolGrid(onOpen = { open = it })
        return
    }
    BackHandler { open = null }
    if (tool == Tool.CALCULATOR) {
        CalculatorTool(onBack = { open = null })
        return
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ToolHeader(tool, onBack = { open = null })
        when (tool) {
            Tool.EMI -> EmiTool(state)
            Tool.PREPAY -> PrepayTool(state)
            Tool.AFFORD -> AffordTool(state)
            Tool.CALCULATOR -> Unit
        }
    }
}

@Composable
internal fun ToolHeader(
    tool: Tool,
    onBack: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("◀ " + stringResource(R.string.tools_back)) }
        Text(stringResource(tool.title), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ToolGrid(onOpen: (Tool) -> Unit) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item(key = "title") {
            Text(
                stringResource(R.string.tools_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // Two per row; a plain Row keeps it cheap (no lazy grid inside a lazy column).
        Tool.entries.chunked(2).forEachIndexed { index, pair ->
            item(key = "row-$index") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    pair.forEach { tool ->
                        Card(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .clickable { onOpen(tool) },
                        ) {
                            Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(tool.emoji, style = MaterialTheme.typography.headlineMedium)
                                Text(stringResource(tool.title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(tool.hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
