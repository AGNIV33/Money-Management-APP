package com.agniv.hisaab.feature.insights.report

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.repository.CategoryRepository
import com.agniv.hisaab.core.domain.repository.ExpenseRepository
import com.agniv.hisaab.core.domain.repository.FamilyRepository
import com.agniv.hisaab.core.domain.repository.IncomeRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.ObserveLoans
import com.agniv.hisaab.core.domain.util.TimeProvider
import com.agniv.hisaab.core.ml.insight.MonthTotal
import com.agniv.hisaab.core.ml.insight.MonthlyReportBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

@Immutable
data class ReportState(
    val loading: Boolean = true,
    val month: YearMonth,
    val current: YearMonth,
    val spent: Money = Money.ZERO,
    val lastMonth: Money = Money.ZERO,
    /** Positive = spent more than last month. Null when last month had nothing. */
    val change: Int? = null,
    val slices: List<Slice> = emptyList(),
    val biggest: Expense? = null,
    val monthlyEmi: Money = Money.ZERO,
    /** Private income on this phone; null = not set. */
    val income: Money? = null,
    val trend: List<MonthTotal> = emptyList(),
    val tips: List<Tip> = emptyList(),
    val categories: Map<String, Category> = emptyMap(),
) {
    val savings: Money? get() = income?.let { it - spent }
    val savingsPercent: Int? get() = savings?.let { ReportMath.share(it, income) }
    val emiPercent: Int? get() = ReportMath.share(monthlyEmi, income)
    val isEmpty: Boolean get() = !loading && spent.paise <= 0
}

/**
 * Numbers for the Monthly Report and Insights boards: any month's total and change, category slices, biggest
 * expense, EMI and savings share (only when the private income is set), six-month trend and plain tips.
 * Everything is worked out on the phone from what the viewer may see.
 */
@HiltViewModel
class ReportViewModel
    // Injected by Hilt; the report really reads all of these.
    @Suppress("LongParameterList")
    @Inject
    constructor(
        private val sessions: SessionRepository,
        private val expenses: ExpenseRepository,
        private val categories: CategoryRepository,
        private val families: FamilyRepository,
        private val observeLoans: ObserveLoans,
        private val incomes: IncomeRepository,
        dispatchers: DispatcherProvider,
        time: TimeProvider,
    ) : ViewModel() {
        private val today = time.today()
        private val current = YearMonth.from(today)
        private val month = MutableStateFlow(current)

        fun setMonth(value: YearMonth) {
            month.value = minOf(value, current)
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<ReportState> =
            combine(sessions.session.filterNotNull(), month) { s, m -> s to m }
                .flatMapLatest { (session, m) ->
                    // From six months before the chosen month (for the trend) up to today.
                    val from = m.minusMonths(MonthlyReportBuilder.TREND_MONTHS - 1L).atDay(1)
                    combine(
                        expenses.observeExpenses(session.familyId, session.userId, from, today),
                        categories.observeCategories(session.familyId),
                        families.observeFamily(session.familyId),
                        observeLoans(session),
                        incomes.income,
                    ) { list, cats, family, loans, income ->
                        val inMonth = ReportMath.inMonth(list, m)
                        val spent = ReportMath.total(inMonth)
                        val last = ReportMath.total(ReportMath.inMonth(list, m.minusMonths(1)))
                        val change = ReportMath.changeVsLast(spent, last)
                        val slices = ReportMath.slices(inMonth)
                        val end = if (m == current) today else m.atEndOfMonth()
                        val report = MonthlyReportBuilder.build(end, list, family?.monthlyBudget)
                        val saved = income?.let { ReportMath.share(it - spent, it) }
                        ReportState(
                            loading = false,
                            month = m,
                            current = current,
                            spent = spent,
                            lastMonth = last,
                            change = change,
                            slices = slices,
                            biggest = inMonth.maxByOrNull { it.amount.paise },
                            monthlyEmi = loans.monthlyEmi,
                            income = income,
                            trend = report.trend,
                            tips = ReportMath.tips(slices, saved, change),
                            categories = cats.associateBy { it.id },
                        )
                    }
                }.flowOn(dispatchers.default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReportState(month = current, current = current))

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
