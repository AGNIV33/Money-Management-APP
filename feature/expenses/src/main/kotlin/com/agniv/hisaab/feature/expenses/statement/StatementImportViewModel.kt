package com.agniv.hisaab.feature.expenses.statement

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.model.Source
import com.agniv.hisaab.core.domain.repository.CategoryRepository
import com.agniv.hisaab.core.domain.repository.ExpenseRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.AddExpense
import com.agniv.hisaab.core.domain.usecase.ExpenseDraft
import com.agniv.hisaab.core.domain.usecase.ObserveLoans
import com.agniv.hisaab.core.domain.usecase.SetEmiPaid
import com.agniv.hisaab.core.domain.util.TimeProvider
import com.agniv.hisaab.core.ml.statement.MatchResult
import com.agniv.hisaab.core.ml.statement.MatchSummary
import com.agniv.hisaab.core.ml.statement.MatchedLine
import com.agniv.hisaab.core.ml.statement.StatementMatcher
import com.agniv.hisaab.core.ml.statement.StatementParser
import com.agniv.hisaab.core.ml.statement.summary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StatementPhase { PICK, READING, REVIEW, DONE }

enum class StatementMessage { NO_TEXT, NO_ROWS, PDF_LOCKED, READ_FAILED, SAVE_FAILED }

/** One payment from the statement, with the user's choice about it. */
@Immutable
data class ReviewRow(
    val key: Int,
    val matched: MatchedLine,
    val selected: Boolean,
    val categoryId: String?,
) {
    /** Rows the user can act on: add as an expense, or tick an EMI. */
    val actionable: Boolean get() = matched.result !is MatchResult.Added && matched.result !is MatchResult.EmiMarked
}

@Immutable
data class StatementUiState(
    val phase: StatementPhase = StatementPhase.PICK,
    val progress: String? = null,
    val rows: List<ReviewRow> = emptyList(),
    val summary: MatchSummary? = null,
    val categories: List<Category> = emptyList(),
    val message: StatementMessage? = null,
    val saving: Boolean = false,
    val addedCount: Int = 0,
) {
    val selectedCount: Int get() = rows.count { it.selected && it.actionable }
}

/**
 * Passbook / statement import (PRD F21): read, compare with Kinly, let the user pick what to add.
 * AI suggests, the user confirms: nothing is saved until "Add selected" is pressed.
 */
@HiltViewModel
class StatementImportViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        categories: CategoryRepository,
        private val expenses: ExpenseRepository,
        private val observeLoans: ObserveLoans,
        private val addExpense: AddExpense,
        private val setEmiPaid: SetEmiPaid,
        private val time: TimeProvider,
    ) : ViewModel() {
        private val _state = MutableStateFlow(StatementUiState())
        val state: StateFlow<StatementUiState> = _state

        init {
            observeCategories(categories)
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun observeCategories(categories: CategoryRepository) {
            viewModelScope.launch {
                sessions.session
                    .filterNotNull()
                    .flatMapLatest { categories.observeCategories(it.familyId) }
                    .collect { list -> _state.update { it.copy(categories = list) } }
            }
        }

        fun reset() = _state.update { StatementUiState(categories = it.categories) }

        fun onReading(progress: String? = null) =
            _state.update {
                it.copy(
                    phase = StatementPhase.READING,
                    progress = progress,
                    message = null,
                )
            }

        fun onFailure(message: StatementMessage) =
            _state.update {
                it.copy(
                    phase = StatementPhase.PICK,
                    progress = null,
                    message = message,
                )
            }

        /** All the text read from the photos or PDF pages. */
        fun onText(text: String) {
            if (text.isBlank()) {
                onFailure(StatementMessage.NO_TEXT)
                return
            }
            viewModelScope.launch {
                val lines = StatementParser.parse(text, time.today())
                if (lines.isEmpty()) {
                    onFailure(StatementMessage.NO_ROWS)
                    return@launch
                }
                val session = sessions.session.filterNotNull().first()
                val from = lines.minOf { it.date }.minusDays(WINDOW_DAYS)
                val to = lines.maxOf { it.date }.plusDays(WINDOW_DAYS)
                val existing = expenses.observeExpenses(session.familyId, session.userId, from, to).first()
                val loans = observeLoans(session).first().items
                val matched = StatementMatcher.match(lines, existing, loans)
                if (matched.isEmpty()) {
                    onFailure(StatementMessage.NO_ROWS)
                    return@launch
                }
                _state.update { s ->
                    val rows =
                        matched.mapIndexed { i, m ->
                            val missing = m.result as? MatchResult.Missing
                            val category =
                                s.categories.firstOrNull { it.name == missing?.categoryName }
                                    ?: s.categories.firstOrNull { it.name == OTHERS }
                            // Missing payments and EMIs are ticked for the user; "not sure" ones are not.
                            ReviewRow(
                                i,
                                m,
                                selected = m.result is MatchResult.Missing || m.result is MatchResult.EmiUnmarked,
                                categoryId = category?.id,
                            )
                        }
                    s.copy(phase = StatementPhase.REVIEW, progress = null, rows = rows, summary = matched.summary(), message = null)
                }
            }
        }

        fun toggle(key: Int) =
            _state.update { s ->
                s.copy(
                    rows =
                        s.rows.map {
                            if (it.key ==
                                key
                            ) {
                                it.copy(selected = !it.selected)
                            } else {
                                it
                            }
                        },
                )
            }

        fun setCategory(
            key: Int,
            categoryId: String,
        ) = _state.update { s -> s.copy(rows = s.rows.map { if (it.key == key) it.copy(categoryId = categoryId) else it }) }

        fun addSelected() {
            val s = _state.value
            if (s.saving) return
            _state.update { it.copy(saving = true) }
            viewModelScope.launch {
                val session = sessions.session.filterNotNull().first()
                var done = 0
                var failed = false
                for (row in s.rows.filter { it.selected && it.actionable }) {
                    val line = row.matched.line
                    val ok =
                        when (val r = row.matched.result) {
                            is MatchResult.EmiUnmarked -> setEmiPaid(session, r.loan, r.month, paid = true).isSuccess
                            is MatchResult.Missing -> save(session, row, r.mode, r.note)
                            is MatchResult.Unsure -> save(session, row, null, line.description.take(NOTE_MAX))
                            else -> true
                        }
                    if (ok) done++ else failed = true
                }
                _state.update {
                    if (failed) {
                        it.copy(saving = false, message = StatementMessage.SAVE_FAILED)
                    } else {
                        it.copy(saving = false, phase = StatementPhase.DONE, addedCount = done)
                    }
                }
            }
        }

        private suspend fun save(
            session: Session,
            row: ReviewRow,
            mode: PayMode?,
            note: String?,
        ): Boolean {
            val categoryId = row.categoryId ?: return false
            val line = row.matched.line
            val draft = ExpenseDraft(line.amount, categoryId, line.date, note, mode ?: PayMode.UPI, source = Source.SCAN)
            return addExpense(session, draft).isSuccess
        }

        private companion object {
            const val OTHERS = "Others"
            const val WINDOW_DAYS = 10L
            const val NOTE_MAX = 60
        }
    }
