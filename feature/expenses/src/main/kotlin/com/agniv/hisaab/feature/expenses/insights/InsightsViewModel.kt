package com.agniv.hisaab.feature.expenses.insights

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.domain.model.AiReportException
import com.agniv.hisaab.core.domain.model.AiReportFailure
import com.agniv.hisaab.core.domain.model.AiTip
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.repository.AiReportRepository
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
import com.agniv.hisaab.core.ml.report.ReportPayloadBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

enum class ReportPhase { IDLE, LOADING, READY, ERROR }

/** The AI saving tips for last month. Only made when the user asks; falls back silently to the on-device insights. */
@Immutable
data class ReportUiState(
    val phase: ReportPhase = ReportPhase.IDLE,
    val month: YearMonth,
    val tips: List<AiTip> = emptyList(),
    val failure: AiReportFailure? = null,
)

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
        recurring: RecurringRepository,
        private val observeLoans: ObserveLoans,
        private val aiReports: AiReportRepository,
        private val sessions: SessionRepository,
        private val expenses: ExpenseRepository,
        private val categories: CategoryRepository,
        private val families: FamilyRepository,
        dispatchers: DispatcherProvider,
        private val time: TimeProvider,
    ) : ViewModel() {
        private val reportMonth = YearMonth.from(time.today()).minusMonths(1)
        private val _report = MutableStateFlow(ReportUiState(month = reportMonth))
        val report: StateFlow<ReportUiState> = _report

        /** Asks the server for last month's tips. Sends only category totals (see ReportPayloadBuilder). */
        fun requestTips(language: String) {
            if (_report.value.phase == ReportPhase.LOADING) return
            _report.update { it.copy(phase = ReportPhase.LOADING, failure = null) }
            viewModelScope.launch {
                val session = sessions.session.filterNotNull().first()
                val from = reportMonth.minusMonths(1).atDay(1)
                val list = expenses.observeExpenses(session.familyId, session.userId, from, reportMonth.atEndOfMonth()).first()
                val request =
                    ReportPayloadBuilder.build(
                        session.familyId,
                        reportMonth,
                        language,
                        list,
                        categories.observeCategories(session.familyId).first(),
                        families.observeFamily(session.familyId).first()?.monthlyBudget,
                        observeLoans(session).first().monthlyEmi,
                    )
                if (request.totalPaise <= 0) {
                    _report.update { it.copy(phase = ReportPhase.ERROR, failure = AiReportFailure.FAILED) }
                    return@launch
                }
                aiReports
                    .monthlyTips(request)
                    .onSuccess { tips -> _report.update { it.copy(phase = ReportPhase.READY, tips = tips) } }
                    .onFailure { e ->
                        val why = (e as? AiReportException)?.failure ?: AiReportFailure.FAILED
                        _report.update { it.copy(phase = ReportPhase.ERROR, failure = why) }
                    }
            }
        }

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
