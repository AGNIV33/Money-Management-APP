package com.agniv.hisaab.feature.loans

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.LoanType
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.repository.LoanRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.LoanDraft
import com.agniv.hisaab.core.domain.usecase.LoanError
import com.agniv.hisaab.core.domain.usecase.SaveLoan
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

enum class LoanField { LENDER, AMOUNT, RATE, TENURE, EMI, SAVE }

@Immutable
data class AddLoanUiState(
    val editingId: String? = null,
    val type: LoanType = LoanType.HOME,
    val lender: String = "",
    val amount: String = "",
    val rate: String = "",
    val tenure: String = "",
    val tenureInYears: Boolean = true,
    /** Empty = use the formula EMI shown as a hint. */
    val emi: String = "",
    val firstEmiDate: LocalDate,
    val onlyMe: Boolean = false,
    val error: LoanField? = null,
    val saving: Boolean = false,
) {
    val formulaEmi: Money? get() = LoanForm.formulaEmi(amount, rate, tenure, tenureInYears)
}

sealed interface AddLoanEvent {
    data class Type(
        val type: LoanType,
    ) : AddLoanEvent

    data class Lender(
        val text: String,
    ) : AddLoanEvent

    data class Amount(
        val text: String,
    ) : AddLoanEvent

    data class Rate(
        val text: String,
    ) : AddLoanEvent

    data class Tenure(
        val text: String,
    ) : AddLoanEvent

    data class TenureUnit(
        val years: Boolean,
    ) : AddLoanEvent

    data class Emi(
        val text: String,
    ) : AddLoanEvent

    data class FirstEmi(
        val date: LocalDate,
    ) : AddLoanEvent

    data class OnlyMe(
        val on: Boolean,
    ) : AddLoanEvent

    data object Save : AddLoanEvent
}

@HiltViewModel
class AddLoanViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        private val loans: LoanRepository,
        private val saveLoan: SaveLoan,
        time: TimeProvider,
    ) : ViewModel() {
        // Most loans start next month on today's date; the user can change it.
        private val _state = MutableStateFlow(AddLoanUiState(firstEmiDate = time.today().plusMonths(1)))
        val state: StateFlow<AddLoanUiState> = _state
        private val saved = Channel<Unit>(Channel.CONFLATED)
        val savedEvents = saved.receiveAsFlow()
        private var started = false

        fun start(editId: String?) {
            if (started) return
            started = true
            if (editId == null) return
            viewModelScope.launch {
                val loan = loans.getLoan(editId) ?: return@launch
                val years = loan.tenureMonths % MONTHS_PER_YEAR == 0
                _state.update {
                    it.copy(
                        editingId = loan.id,
                        type = loan.type,
                        lender = loan.lender,
                        amount = LoanForm.amountText(loan.principal),
                        rate = LoanForm.rateText(loan.annualRateBps),
                        tenure = (if (years) loan.tenureMonths / MONTHS_PER_YEAR else loan.tenureMonths).toString(),
                        tenureInYears = years,
                        emi = LoanForm.amountText(loan.emi),
                        firstEmiDate = loan.firstEmiDate,
                        onlyMe = loan.visibility == Visibility.PRIVATE,
                    )
                }
            }
        }

        fun onEvent(event: AddLoanEvent) {
            when (event) {
                is AddLoanEvent.Type -> _state.update { it.copy(type = event.type) }
                is AddLoanEvent.Lender -> _state.update { it.copy(lender = event.text, error = null) }
                is AddLoanEvent.Amount -> _state.update { it.copy(amount = event.text, error = null) }
                is AddLoanEvent.Rate -> _state.update { it.copy(rate = event.text, error = null) }
                is AddLoanEvent.Tenure -> _state.update { it.copy(tenure = event.text, error = null) }
                is AddLoanEvent.TenureUnit -> _state.update { it.copy(tenureInYears = event.years, error = null) }
                is AddLoanEvent.Emi -> _state.update { it.copy(emi = event.text, error = null) }
                is AddLoanEvent.FirstEmi -> _state.update { it.copy(firstEmiDate = event.date) }
                is AddLoanEvent.OnlyMe -> _state.update { it.copy(onlyMe = event.on) }
                AddLoanEvent.Save -> save()
            }
        }

        private fun save() {
            val s = _state.value
            if (s.saving) return
            val draft = s.toDraft()
            if (draft == null) {
                _state.update { it.copy(error = s.firstBadField()) }
                return
            }
            _state.update { it.copy(saving = true) }
            viewModelScope.launch {
                val session = sessions.session.filterNotNull().first()
                saveLoan(session, draft, s.editingId)
                    .onSuccess { saved.send(Unit) }
                    .onFailure { e -> _state.update { it.copy(saving = false, error = e.toField()) } }
            }
        }

        private fun Throwable.toField(): LoanField =
            when (this) {
                LoanError.NoLender, LoanError.LenderTooLong -> LoanField.LENDER
                LoanError.BadPrincipal -> LoanField.AMOUNT
                LoanError.BadRate -> LoanField.RATE
                LoanError.BadTenure -> LoanField.TENURE
                LoanError.BadEmi, LoanError.EmiTooSmall -> LoanField.EMI
                else -> LoanField.SAVE
            }

        private companion object {
            const val MONTHS_PER_YEAR = 12
        }
    }

/** First field the user must fix, top to bottom, or null when every field reads cleanly. */
internal fun AddLoanUiState.firstBadField(): LoanField? =
    when {
        lender.isBlank() -> LoanField.LENDER
        LoanForm.parseAmount(amount) == null -> LoanField.AMOUNT
        LoanForm.parseRateBps(rate) == null -> LoanField.RATE
        LoanForm.parseTenureMonths(tenure, tenureInYears) == null -> LoanField.TENURE
        emi.isNotBlank() && LoanForm.parseAmount(emi) == null -> LoanField.EMI
        else -> null
    }

/** The form as a draft for [SaveLoan], or null while a field is not readable. */
internal fun AddLoanUiState.toDraft(): LoanDraft? {
    if (firstBadField() != null) return null
    val principal = LoanForm.parseAmount(amount)
    val bps = LoanForm.parseRateBps(rate)
    val months = LoanForm.parseTenureMonths(tenure, tenureInYears)
    // Always non-null once firstBadField() passed; checked again only to avoid !!.
    return if (principal != null && bps != null && months != null) {
        LoanDraft(
            type = type,
            lender = lender,
            principal = principal,
            annualRateBps = bps,
            tenureMonths = months,
            emi = emi.takeIf { it.isNotBlank() }?.let(LoanForm::parseAmount),
            firstEmiDate = firstEmiDate,
            visibility = if (onlyMe) Visibility.PRIVATE else Visibility.FAMILY,
        )
    } else {
        null
    }
}
