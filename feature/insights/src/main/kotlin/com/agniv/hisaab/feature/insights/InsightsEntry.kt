package com.agniv.hisaab.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * What Home shows for insights: the top insight card, and the full Insights sheet when it is tapped.
 * [openRequest] opens the sheet from outside (a tapped smart alert or report notification); [onOpenHandled] clears it.
 */
@Composable
fun InsightsHomeCard(
    openRequest: Boolean = false,
    onOpenHandled: () -> Unit = {},
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSheet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openRequest) {
        if (openRequest) {
            showSheet = true
            onOpenHandled()
        }
    }
    HomeInsightCard(state, onOpen = { showSheet = true })
    if (showSheet) {
        val report by viewModel.report.collectAsStateWithLifecycle()
        InsightsSheet(
            state,
            report,
            onGetTips = viewModel::requestTips,
            onSetIncome = viewModel::setIncome,
            onDismiss = { showSheet = false },
        )
    }
}
