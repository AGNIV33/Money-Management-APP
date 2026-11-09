package com.agniv.hisaab.feature.expenses.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.repository.CategoryRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.DeleteExpense
import com.agniv.hisaab.core.domain.usecase.MonthSummary
import com.agniv.hisaab.core.domain.usecase.ObserveMonthExpenses
import com.agniv.hisaab.core.domain.usecase.ObserveMonthSummary
import com.agniv.hisaab.core.domain.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@Immutable
data class ExpensesHomeUiState(
    val loading: Boolean = true,
    val month: YearMonth? = null,
    val summary: MonthSummary? = null,
    val expenses: List<Expense> = emptyList(),
    val categories: Map<String, Category> = emptyMap(),
)

@HiltViewModel
class ExpensesHomeViewModel
    @Inject
    constructor(
        sessions: SessionRepository,
        categories: CategoryRepository,
        observeMonthExpenses: ObserveMonthExpenses,
        observeMonthSummary: ObserveMonthSummary,
        private val deleteExpense: DeleteExpense,
        time: TimeProvider,
    ) : ViewModel() {
        private val month = YearMonth.from(time.today())

        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<ExpensesHomeUiState> =
            sessions.session
                .filterNotNull()
                .flatMapLatest { session ->
                    combine(
                        observeMonthSummary(session, month),
                        observeMonthExpenses(session, month),
                        categories.observeCategories(session.familyId),
                    ) { summary, expenses, cats ->
                        ExpensesHomeUiState(
                            loading = false,
                            month = month,
                            summary = summary,
                            expenses = expenses,
                            categories = cats.associateBy { it.id },
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExpensesHomeUiState())

        fun delete(id: String) {
            viewModelScope.launch { deleteExpense(id) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
