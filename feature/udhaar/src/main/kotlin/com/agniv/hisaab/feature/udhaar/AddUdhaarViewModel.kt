package com.agniv.hisaab.feature.udhaar

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.UdhaarDirection
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.repository.UdhaarRepository
import com.agniv.hisaab.core.domain.usecase.SaveUdhaar
import com.agniv.hisaab.core.domain.usecase.UdhaarDraft
import com.agniv.hisaab.core.domain.usecase.UdhaarError
import com.agniv.hisaab.core.domain.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class UdhaarField { PERSON, AMOUNT, NOTE, SAVE }

@Immutable
data class AddUdhaarUiState(
    val editingId: String? = null,
    val direction: UdhaarDirection = UdhaarDirection.THEY_OWE_ME,
    val person: String = "",
    val amount: String = "",
    val date: LocalDate,
    val dueDate: LocalDate? = null,
    val note: String = "",
    val onlyMe: Boolean = false,
    val error: UdhaarField? = null,
    val saving: Boolean = false,
)

sealed interface AddUdhaarEvent {
    data class Direction(
        val direction: UdhaarDirection,
    ) : AddUdhaarEvent

    data class Person(
        val text: String,
    ) : AddUdhaarEvent

    data class Amount(
        val text: String,
    ) : AddUdhaarEvent

    data class Date(
        val date: LocalDate,
    ) : AddUdhaarEvent

    data class Due(
        val date: LocalDate?,
    ) : AddUdhaarEvent

    data class Note(
        val text: String,
    ) : AddUdhaarEvent

    data class OnlyMe(
        val on: Boolean,
    ) : AddUdhaarEvent

    data object Save : AddUdhaarEvent
}

@HiltViewModel
class AddUdhaarViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        private val repository: UdhaarRepository,
        private val saveUdhaar: SaveUdhaar,
        time: TimeProvider,
    ) : ViewModel() {
        private val _state = MutableStateFlow(AddUdhaarUiState(date = time.today()))
        val state: StateFlow<AddUdhaarUiState> = _state
        private val saved = Channel<Unit>(Channel.CONFLATED)
        val savedEvents = saved.receiveAsFlow()
        private var started = false

        fun start(
            editId: String?,
            direction: UdhaarDirection,
        ) {
            if (started) return
            started = true
            _state.update { it.copy(direction = direction) }
            if (editId == null) return
            viewModelScope.launch {
                val e = repository.get(editId) ?: return@launch
                _state.update {
                    it.copy(
                        editingId = e.id,
                        direction = e.direction,
                        person = e.person,
                        amount = amountText(e.amount),
                        date = e.date,
                        dueDate = e.dueDate,
                        note = e.note.orEmpty(),
                        onlyMe = e.visibility == Visibility.PRIVATE,
                    )
                }
            }
        }

        fun onEvent(event: AddUdhaarEvent) {
            when (event) {
                is AddUdhaarEvent.Direction -> _state.update { it.copy(direction = event.direction) }
                is AddUdhaarEvent.Person -> _state.update { it.copy(person = event.text, error = null) }
                is AddUdhaarEvent.Amount -> _state.update { it.copy(amount = event.text, error = null) }
                is AddUdhaarEvent.Date -> _state.update { it.copy(date = event.date) }
                is AddUdhaarEvent.Due -> _state.update { it.copy(dueDate = event.date) }
                is AddUdhaarEvent.Note -> _state.update { it.copy(note = event.text, error = null) }
                is AddUdhaarEvent.OnlyMe -> _state.update { it.copy(onlyMe = event.on) }
                AddUdhaarEvent.Save -> save()
            }
        }

        private fun save() {
            val s = _state.value
            if (s.saving) return
            val amount = Money.parse(s.amount)?.takeIf { it.paise > 0 }
            if (amount == null) {
                _state.update { it.copy(error = UdhaarField.AMOUNT) }
                return
            }
            _state.update { it.copy(saving = true) }
            viewModelScope.launch {
                val session = sessions.session.filterNotNull().first()
                val draft =
                    UdhaarDraft(
                        direction = s.direction,
                        person = s.person,
                        amount = amount,
                        date = s.date,
                        dueDate = s.dueDate,
                        note = s.note,
                        visibility = if (s.onlyMe) Visibility.PRIVATE else Visibility.FAMILY,
                    )
                saveUdhaar(session, draft, s.editingId)
                    .onSuccess { saved.send(Unit) }
                    .onFailure { e -> _state.update { it.copy(saving = false, error = e.toField()) } }
            }
        }

        private fun Throwable.toField(): UdhaarField =
            when (this) {
                UdhaarError.NoPerson, UdhaarError.PersonTooLong -> UdhaarField.PERSON
                UdhaarError.BadAmount -> UdhaarField.AMOUNT
                UdhaarError.NoteTooLong -> UdhaarField.NOTE
                else -> UdhaarField.SAVE
            }

        private fun amountText(money: Money): String =
            if (money.paisePart == 0L) "${money.rupeesPart}" else "${money.rupeesPart}.${money.paisePart.toString().padStart(2, '0')}"
    }
