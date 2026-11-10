package com.agniv.hisaab.feature.tools

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.LoanItem
import com.agniv.hisaab.core.domain.usecase.ObserveLoans
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The calculators keep their own inputs; this only offers the family's loans to fill them from. */
@Immutable
data class ToolsUiState(
    val openLoans: List<LoanItem> = emptyList(),
    val monthlyEmi: Money = Money.ZERO,
)

@HiltViewModel
class ToolsViewModel
    @Inject
    constructor(
        sessions: SessionRepository,
        observeLoans: ObserveLoans,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<ToolsUiState> =
            sessions.session
                .filterNotNull()
                .flatMapLatest { observeLoans(it) }
                .map { overview ->
                    ToolsUiState(overview.items.filter { !it.progress.isClosed }, overview.monthlyEmi)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ToolsUiState())

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
