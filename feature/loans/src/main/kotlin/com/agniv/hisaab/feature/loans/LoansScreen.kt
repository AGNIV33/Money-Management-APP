package com.agniv.hisaab.feature.loans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.HSummaryCard
import com.agniv.hisaab.core.designsystem.theme.Spacing

/** Loans tab (DESIGN.md §7.8): summary, loan cards, and a loan's detail in place of the list. */
@Composable
fun LoansScreen(viewModel: LoansViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    // null = closed, "" = adding, otherwise the id being edited.
    var sheetFor by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetCount by rememberSaveable { mutableIntStateOf(0) }
    val openSheet: (String) -> Unit = {
        sheetCount++
        sheetFor = it
    }

    val open = state.overview?.items?.firstOrNull { it.loan.id == openId }
    if (openId != null && open != null) {
        BackHandler { openId = null }
        LoanDetail(
            item = open,
            today = state.today,
            onBack = { openId = null },
            onEdit = { openSheet(open.loan.id) },
            onDelete = {
                viewModel.onEvent(LoansEvent.Delete(open.loan.id))
                openId = null
            },
            onSetPaid = { month, paid -> viewModel.onEvent(LoansEvent.SetPaid(open.loan.id, month, paid)) },
        )
    } else {
        LoansList(state, onOpen = { openId = it }, onAdd = { openSheet("") })
    }

    sheetFor?.let { id -> AddLoanSheet(onDismiss = { sheetFor = null }, editId = id.ifEmpty { null }, instanceKey = "$sheetCount") }
}

@Composable
private fun LoansList(
    state: LoansUiState,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                text = { Text(stringResource(R.string.loans_add_fab)) },
                icon = { Text("+", style = MaterialTheme.typography.headlineSmall) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item(key = "title") {
                Text(
                    stringResource(R.string.loans_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val overview = state.overview
            if (overview != null && overview.items.isNotEmpty()) {
                item(key = "summary") {
                    HSummaryCard(
                        title = stringResource(R.string.loans_monthly_emi),
                        amount = overview.monthlyEmi,
                        subtitle = stringResource(R.string.loans_outstanding, overview.outstanding.formatted()),
                    )
                }
                items(overview.items, key = { it.loan.id }) { item ->
                    LoanCard(item, onClick = { onOpen(item.loan.id) })
                }
            } else if (!state.loading) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.loans_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.xl),
                    )
                }
            }
        }
    }
}
