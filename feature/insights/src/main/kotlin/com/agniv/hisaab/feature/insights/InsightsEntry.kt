package com.agniv.hisaab.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Home's "Spending Insight" card; tapping it opens the Insights tab ([onOpen], wired by the app). */
@Composable
fun InsightsHomeCard(
    onOpen: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeInsightCard(state, onOpen = onOpen)
}

/** The Insights tab: report card, insights, private income and AI tips. */
@Composable
fun InsightsScreen(viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    InsightsContent(state, report, onGetTips = viewModel::requestTips, onSetIncome = viewModel::setIncome)
}
