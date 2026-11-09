package com.agniv.hisaab.feature.expenses.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.repository.CategoryRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.DeleteExpense
import com.agniv.hisaab.core.domain.usecase.MonthSummary
import com.agniv.hisaab.core.domain.usecase.ObserveMonthExpenses
import com.agniv.hisaab.core.domain.usecase.ObserveMonthSummary
import com.agniv.hisaab.core.domain.usecase.RestoreExpense
import com.agniv.hisaab.core.domain.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@Immutable
data class ExpensesHomeUiState(
    val loading: Boolean = true,
    val month: YearMonth,
    val isCurrentMonth: Boolean = true,
    val summary: MonthSummary? = null,
    val days: List<DayGroup> = emptyList(),
    val filteredTotal: Money = Money.ZERO,
    val hasAnyThisMonth: Boolean = false,
    val filter: ExpenseFilter = ExpenseFilter(),
    val categories: Map<String, Category> = emptyMap(),
)

sealed interface HomeEvent {
    data object PreviousMonth : HomeEvent

    data object NextMonth : HomeEvent

    data class Search(
        val query: String,
    ) : HomeEvent

    data class FilterCategory(
        val id: String?,
    ) : HomeEvent

    data class FilterMode(
        val mode: PayMode?,
    ) : HomeEvent

    data object ClearFilters : HomeEvent

    data class Delete(
        val id: String,
    ) : HomeEvent

    data class Undo(
        val id: String,
    ) : HomeEvent
}

@HiltViewModel
class ExpensesHomeViewModel
    @Inject
    constructor(
        sessions: SessionRepository,
        categories: CategoryRepository,
        observeMonthExpenses: ObserveMonthExpenses,
        observeMonthSummary: ObserveMonthSummary,
        private val deleteExpense: DeleteExpense,
        private val restoreExpense: RestoreExpense,
        time: TimeProvider,
    ) : ViewModel() {
        private val thisMonth = YearMonth.from(time.today())
        private val month = MutableStateFlow(thisMonth)
        private val filter = MutableStateFlow(ExpenseFilter())

        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<ExpensesHomeUiState> =
            combine(sessions.session.filterNotNull(), month) { s, m -> s to m }
                .flatMapLatest { (session, m) ->
                    combine(
                        observeMonthSummary(session, m),
                        observeMonthExpenses(session, m),
                        categories.observeCategories(session.familyId),
                        filter,
                    ) { summary, expenses, cats, f ->
                        val byId = cats.associateBy { it.id }
                        val shown = expenses.applyFilter(f, byId)
                        ExpensesHomeUiState(
                            loading = false,
                            month = m,
                            isCurrentMonth = m == thisMonth,
                            summary = summary,
                            days = shown.groupByDay(),
                            filteredTotal = shown.map { it.amount }.sum(),
                            hasAnyThisMonth = expenses.isNotEmpty(),
                            filter = f,
                            categories = byId,
                        )
                    }
                }.stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    ExpensesHomeUiState(month = thisMonth),
                )

        fun onEvent(event: HomeEvent) {
            when (event) {
                HomeEvent.PreviousMonth -> month.update { it.minusMonths(1) }
                // No future months: nothing can be spent there yet.
                HomeEvent.NextMonth -> month.update { if (it < thisMonth) it.plusMonths(1) else it }
                is HomeEvent.Search -> filter.update { it.copy(query = event.query) }
                is HomeEvent.FilterCategory -> filter.update { it.copy(categoryId = event.id) }
                is HomeEvent.FilterMode -> filter.update { it.copy(mode = event.mode) }
                HomeEvent.ClearFilters -> filter.value = ExpenseFilter()
                is HomeEvent.Delete -> viewModelScope.launch { deleteExpense(event.id) }
                is HomeEvent.Undo -> viewModelScope.launch { restoreExpense(event.id) }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
