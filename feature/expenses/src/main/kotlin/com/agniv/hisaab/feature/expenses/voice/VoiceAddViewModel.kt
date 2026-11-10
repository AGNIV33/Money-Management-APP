package com.agniv.hisaab.feature.expenses.voice

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.Source
import com.agniv.hisaab.core.domain.repository.CategoryRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.AddExpense
import com.agniv.hisaab.core.domain.usecase.ExpenseDraft
import com.agniv.hisaab.core.domain.util.TimeProvider
import com.agniv.hisaab.core.ml.voice.VoiceExpense
import com.agniv.hisaab.core.ml.voice.VoiceParser
import com.agniv.hisaab.feature.expenses.add.ExpensePrefill
import com.agniv.hisaab.feature.expenses.add.toInputText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VoiceLanguage(
    val tag: String,
) {
    ENGLISH("en-IN"),
    HINDI("hi-IN"),
}

enum class VoiceMessage { NO_TEXT, NO_SPEECH, NETWORK, BUSY, OTHER, UNAVAILABLE, NO_PERMISSION, NO_AMOUNT, SAVE_FAILED }

@Immutable
data class VoiceUiState(
    val language: VoiceLanguage = VoiceLanguage.ENGLISH,
    val listening: Boolean = false,
    /** Live text while speaking, then the final sentence. */
    val heard: String = "",
    val result: VoiceExpense? = null,
    val categoryId: String? = null,
    val categories: List<Category> = emptyList(),
    val message: VoiceMessage? = null,
    val saving: Boolean = false,
    /** Where the values came from; stored on the expense. */
    val source: Source = Source.VOICE,
) {
    val canSave: Boolean get() = result?.amount != null && categoryId != null
}

// Small one-line state setters for the sheet (voice and bill scan share them).
@Suppress("TooManyFunctions")
@HiltViewModel
class VoiceAddViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        categories: CategoryRepository,
        private val addExpense: AddExpense,
        private val time: TimeProvider,
    ) : ViewModel() {
        private val _state = MutableStateFlow(VoiceUiState())
        val state: StateFlow<VoiceUiState> = _state

        private val saved = Channel<Unit>(Channel.BUFFERED)

        /** Emits once after a save so the sheet can close. */
        val savedEvents: Flow<Unit> = saved.receiveAsFlow()

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

        fun setLanguage(language: VoiceLanguage) = _state.update { it.copy(language = language) }

        fun onListening() = _state.update { it.copy(listening = true, heard = "", result = null, message = null) }

        fun onPartial(text: String) = _state.update { it.copy(heard = text) }

        fun onFailure(message: VoiceMessage) = _state.update { it.copy(listening = false, message = message) }

        /** A sentence from the microphone or typed in. Parsed on the phone; nothing is saved yet. */
        fun onSentence(text: String) = onParsed(VoiceParser.parse(text, time.today()), text, Source.VOICE)

        /** Anything read into an expense (a sentence or a scanned bill). Nothing is saved until the user confirms. */
        fun onParsed(
            parsed: VoiceExpense,
            heard: String,
            source: Source,
        ) {
            _state.update { s ->
                val category =
                    s.categories.firstOrNull { it.name == parsed.categoryName }
                        // Nothing matched: file it under Others so it can still be saved; the chips let the user change it.
                        ?: s.categories.firstOrNull { it.name == OTHERS }
                s.copy(
                    listening = false,
                    heard = heard,
                    source = source,
                    result = parsed,
                    categoryId = category?.id,
                    message = if (parsed.amount == null) VoiceMessage.NO_AMOUNT else null,
                )
            }
        }

        fun pickCategory(id: String) = _state.update { it.copy(categoryId = id) }

        fun reset() = _state.update { VoiceUiState(language = it.language, categories = it.categories) }

        fun prefill(): ExpensePrefill? {
            val s = _state.value
            val r = s.result ?: return null
            return ExpensePrefill(r.amount?.toInputText().orEmpty(), s.categoryId, r.date, r.note.orEmpty(), r.mode)
        }

        fun save() {
            val s = _state.value
            val r = s.result
            val amount = r?.amount
            val categoryId = s.categoryId
            if (s.saving) return
            if (r == null || amount == null || categoryId == null) return
            _state.update { it.copy(saving = true) }
            viewModelScope.launch {
                val session = sessions.session.filterNotNull().first()
                val draft =
                    ExpenseDraft(
                        amount = amount,
                        categoryId = categoryId,
                        date = r.date,
                        note = r.note,
                        mode = r.mode ?: PayMode.UPI,
                        source = s.source,
                    )
                addExpense(session, draft)
                    .onSuccess { saved.send(Unit) }
                    .onFailure { _state.update { it.copy(saving = false, message = VoiceMessage.SAVE_FAILED) } }
            }
        }

        private companion object {
            const val OTHERS = "Others"
        }
    }
