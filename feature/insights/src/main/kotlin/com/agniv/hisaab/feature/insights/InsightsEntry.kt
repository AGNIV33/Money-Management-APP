package com.agniv.hisaab.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.feature.insights.report.ReportViewModel

/** Home's "Spending Insight" card; tapping it opens the Insights tab ([onOpen], wired by the app). */
@Composable
fun InsightsHomeCard(
    onOpen: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeInsightCard(state, onOpen = onOpen)
}

/** The Insights tab (board "Insights"). */
@Composable
fun InsightsScreen(
    onReport: () -> Unit,
    onAddExpense: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
    numbers: ReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val month by numbers.state.collectAsStateWithLifecycle()
    InsightsContent(
        state,
        report,
        month,
        onGetTips = viewModel::requestTips,
        onSetIncome = viewModel::setIncome,
        onReport = onReport,
        onAddExpense = onAddExpense,
    )
}
