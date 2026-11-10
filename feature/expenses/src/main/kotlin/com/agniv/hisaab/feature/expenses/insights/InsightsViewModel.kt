package com.agniv.hisaab.feature.expenses.insights

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.repository.CategoryRepository
import com.agniv.hisaab.core.domain.repository.ExpenseRepository
import com.agniv.hisaab.core.domain.repository.FamilyRepository
import com.agniv.hisaab.core.domain.repository.RecurringRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.ObserveLoans
import com.agniv.hisaab.core.domain.util.TimeProvider
import com.agniv.hisaab.core.ml.insight.Insight
import com.agniv.hisaab.core.ml.insight.InsightEngine
import com.agniv.hisaab.core.ml.insight.InsightInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

@Immutable
data class InsightsUiState(
    val insights: List<Insight> = emptyList(),
    val categories: Map<String, Category> = emptyMap(),
)

/** Runs the on-device [InsightEngine] on what the viewer can see; recalculates whenever an entry changes. */
@HiltViewModel
class InsightsViewModel
    @Inject
    constructor(
        sessions: SessionRepository,
        expenses: ExpenseRepository,
        categories: CategoryRepository,
        families: FamilyRepository,
        recurring: RecurringRepository,
        observeLoans: ObserveLoans,
        dispatchers: DispatcherProvider,
        time: TimeProvider,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<InsightsUiState> =
            sessions.session
                .filterNotNull()
                .flatMapLatest { session ->
                    val today = time.today()
                    val from = YearMonth.from(today).minusMonths(HISTORY_MONTHS).atDay(1)
                    combine(
                        expenses.observeExpenses(session.familyId, session.userId, from, today),
                        categories.observeCategories(session.familyId),
                        families.observeFamily(session.familyId),
                        recurring.observe(session.familyId, session.userId),
                        observeLoans(session),
                    ) { list, cats, family, repeating, loans ->
                        val insights = InsightEngine.compute(InsightInput(today, list, family?.monthlyBudget, loans.monthlyEmi, repeating))
                        InsightsUiState(insights, cats.associateBy { it.id })
                    }
                }.flowOn(dispatchers.default)
                .map { it }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InsightsUiState())

        private companion object {
            const val HISTORY_MONTHS = 3L
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
