package com.agniv.hisaab.feature.expenses.add

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.designsystem.component.PadKey
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.repository.CategoryRepository
import com.agniv.hisaab.core.domain.repository.ExpenseRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.AddExpense
import com.agniv.hisaab.core.domain.usecase.ExpenseDraft
import com.agniv.hisaab.core.domain.usecase.ExpenseError
import com.agniv.hisaab.core.domain.usecase.UpdateExpense
import com.agniv.hisaab.core.domain.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class AddError { AMOUNT, CATEGORY, NOTE, SAVE_FAILED }

@Immutable
data class AddExpenseUiState(
    /** Null = adding a new expense; otherwise the id being edited. */
    val editingId: String? = null,
    val amountText: String = "",
    val categories: List<Category> = emptyList(),
    val categoryId: String? = null,
    val date: LocalDate,
    val today: LocalDate,
    val mode: PayMode = PayMode.UPI,
    val note: String = "",
    val onlyMe: Boolean = false,
    val saving: Boolean = false,
    val error: AddError? = null,
) {
    val amount: Money? get() = Money.parse(amountText)
}

sealed interface AddExpenseEvent {
    data class Key(
        val key: PadKey,
    ) : AddExpenseEvent

    data class PickCategory(
        val id: String,
    ) : AddExpenseEvent

    data class PickDate(
        val date: LocalDate,
    ) : AddExpenseEvent

    data class PickMode(
        val mode: PayMode,
    ) : AddExpenseEvent

    data class EditNote(
        val note: String,
    ) : AddExpenseEvent

    data class SetOnlyMe(
        val onlyMe: Boolean,
    ) : AddExpenseEvent

    data object Save : AddExpenseEvent
}

@HiltViewModel
class AddExpenseViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        categories: CategoryRepository,
        private val addExpense: AddExpense,
        private val updateExpense: UpdateExpense,
        private val expenses: ExpenseRepository,
        private val time: TimeProvider,
    ) : ViewModel() {
        private val _state = MutableStateFlow(AddExpenseUiState(date = time.today(), today = time.today()))
        val state: StateFlow<AddExpenseUiState> = _state.asStateFlow()

        private val saved = Channel<Unit>(Channel.BUFFERED)

        /** Emits once after a successful save so the sheet can close. */
        val savedEvents: Flow<Unit> = saved.receiveAsFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun observeCategories(categories: CategoryRepository) {
            viewModelScope.launch {
                sessions.session
                    .filterNotNull()
                    .flatMapLatest { categories.observeCategories(it.familyId) }
                    .collect { list -> _state.update { it.copy(categories = list) } }
            }
        }

        init {
            observeCategories(categories)
        }

        /**
         * Call each time the sheet opens. The ViewModel outlives one sheet, so this clears the last entry
         * but keeps the last payment mode (DESIGN.md §7.4: "remembers last").
         */
        fun start(editId: String?) {
            val today = time.today()
            _state.update {
                AddExpenseUiState(categories = it.categories, date = today, today = today, mode = it.mode)
            }
            if (editId == null) return
            viewModelScope.launch {
                val e = expenses.getExpense(editId) ?: return@launch
                _state.update {
                    it.copy(
                        editingId = e.id,
                        amountText = e.amount.toInputText(),
                        categoryId = e.categoryId,
                        date = e.date,
                        mode = e.mode,
                        note = e.note.orEmpty(),
                        onlyMe = e.visibility == Visibility.PRIVATE,
                    )
                }
            }
        }

        fun onEvent(event: AddExpenseEvent) {
            when (event) {
                is AddExpenseEvent.Key ->
                    _state.update { it.copy(amountText = AmountInput.apply(it.amountText, event.key), error = null) }
                is AddExpenseEvent.PickCategory -> _state.update { it.copy(categoryId = event.id, error = null) }
                is AddExpenseEvent.PickDate -> _state.update { it.copy(date = event.date) }
                is AddExpenseEvent.PickMode -> _state.update { it.copy(mode = event.mode) }
                is AddExpenseEvent.EditNote -> _state.update { it.copy(note = event.note, error = null) }
                is AddExpenseEvent.SetOnlyMe -> _state.update { it.copy(onlyMe = event.onlyMe) }
                AddExpenseEvent.Save -> save()
            }
        }

        private fun save() {
            val current = _state.value
            if (current.saving) return
            val amount = current.amount
            val categoryId = current.categoryId
            when {
                amount == null || amount.paise <= 0 -> _state.update { it.copy(error = AddError.AMOUNT) }
                categoryId == null -> _state.update { it.copy(error = AddError.CATEGORY) }
                else -> {
                    _state.update { it.copy(saving = true) }
                    viewModelScope.launch {
                        val session = sessions.session.filterNotNull().first()
                        val draft =
                            ExpenseDraft(
                                amount = amount,
                                categoryId = categoryId,
                                date = current.date,
                                note = current.note,
                                mode = current.mode,
                                visibility = if (current.onlyMe) Visibility.PRIVATE else Visibility.FAMILY,
                            )
                        val result =
                            current.editingId?.let { updateExpense(it, draft) } ?: addExpense(session, draft)
                        result
                            .onSuccess { saved.send(Unit) }
                            .onFailure { e ->
                                val error = if (e == ExpenseError.NoteTooLong) AddError.NOTE else AddError.SAVE_FAILED
                                _state.update { it.copy(saving = false, error = error) }
                            }
                    }
                }
            }
        }
    }

/** 12550 paise -> "125.5", 12500 -> "125": what the keypad would have produced. */
internal fun Money.toInputText(): String {
    val fraction = paisePart.toString().padStart(2, '0').trimEnd('0')
    val sign = if (isNegative) "-" else ""
    return sign + kotlin.math.abs(rupeesPart) + if (fraction.isEmpty()) "" else ".$fraction"
}
