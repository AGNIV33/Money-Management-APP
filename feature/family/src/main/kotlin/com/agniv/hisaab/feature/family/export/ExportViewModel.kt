package com.agniv.hisaab.feature.family.export

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.domain.export.ExpenseExport
import com.agniv.hisaab.core.domain.export.ExportLabels
import com.agniv.hisaab.core.domain.export.ExportRange
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.repository.CategoryRepository
import com.agniv.hisaab.core.domain.repository.ExpenseRepository
import com.agniv.hisaab.core.domain.repository.MemberRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

enum class ExportFormat { CSV, PDF }

/** Words the files need, read from strings.xml by the screen so the files come out in the app's language. */
data class ExportTexts(
    val labels: ExportLabels,
    val formerMember: String,
    val modeNames: Map<String, String>,
    val hindi: Boolean,
    val pdfTitle: (from: LocalDate, to: LocalDate, count: Int, total: String) -> PdfTitle,
)

@Immutable
data class ExportUiState(
    val range: ExportRange = ExportRange.THIS_MONTH,
    val busy: Boolean = false,
    /** Nothing to export in the chosen range. */
    val empty: Boolean = false,
    val failed: Boolean = false,
    /** Ready to hand to the share sheet; cleared once shared. */
    val file: File? = null,
    val mime: String? = null,
)

/** Export expenses to CSV or PDF (5.6). Works offline: reads only this phone's copy, as the viewer sees it. */
@HiltViewModel
class ExportViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        private val expenses: ExpenseRepository,
        private val categories: CategoryRepository,
        private val members: MemberRepository,
        private val files: ExportFiles,
        private val time: TimeProvider,
        private val dispatchers: DispatcherProvider,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ExportUiState())
        val state: StateFlow<ExportUiState> = _state

        fun setRange(range: ExportRange) = _state.update { it.copy(range = range, empty = false, failed = false) }

        fun shared() = _state.update { it.copy(file = null, mime = null) }

        fun export(
            format: ExportFormat,
            texts: ExportTexts,
        ) {
            if (_state.value.busy) return
            _state.update { it.copy(busy = true, empty = false, failed = false, file = null) }
            viewModelScope.launch {
                val result =
                    runCatching {
                        withContext(dispatchers.io) { write(format, texts) }
                    }
                _state.update { s ->
                    val file = result.getOrNull()
                    s.copy(
                        busy = false,
                        file = file,
                        mime = if (format == ExportFormat.CSV) "text/csv" else "application/pdf",
                        empty = result.isSuccess && file == null,
                        failed = result.isFailure,
                    )
                }
            }
        }

        private suspend fun write(
            format: ExportFormat,
            texts: ExportTexts,
        ): File? {
            val session = sessions.session.filterNotNull().first()
            val today = time.today()
            // The oldest entry decides where "All" starts; the viewer's own query already hides others' Only-me items.
            val all = expenses.observeExpenses(session.familyId, session.userId, LocalDate.of(MIN_YEAR, 1, 1), today).first()
            val (from, to) = _state.value.range.bounds(today, all.filter { !it.deleted }.minOfOrNull { it.date })
            val inRange = all.filter { it.date in from..to }
            val cats = categories.observeCategories(session.familyId).first().associateBy { it.id }
            val names = members.observeMembers(session.familyId).first().associate { it.id to it.name }
            val rows =
                ExpenseExport.rows(
                    inRange,
                    categoryName = { id -> cats[id]?.label(texts.hindi).orEmpty() },
                    memberName = { id -> names[id] ?: texts.formerMember },
                    modeName = { mode -> texts.modeNames[mode] ?: mode },
                )
            if (rows.isEmpty()) return null
            return when (format) {
                ExportFormat.CSV -> files.csv(rows, texts.labels, from, to)
                ExportFormat.PDF -> {
                    val total = ExpenseExport.total(rows)
                    files.pdf(rows, texts.labels, texts.pdfTitle(from, to, rows.size, total.formatted()), from, to)
                }
            }
        }

        private fun Category.label(hindi: Boolean): String = if (hindi && !nameHi.isNullOrBlank()) nameHi.orEmpty() else name

        private companion object {
            const val MIN_YEAR = 2000
        }
    }
