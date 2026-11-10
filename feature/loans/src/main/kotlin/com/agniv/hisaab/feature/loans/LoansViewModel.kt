package com.agniv.hisaab.feature.loans

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.domain.model.Loan
import com.agniv.hisaab.core.domain.model.Role
import com.agniv.hisaab.core.domain.repository.MemberRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.DeleteLoan
import com.agniv.hisaab.core.domain.usecase.LoansOverview
import com.agniv.hisaab.core.domain.usecase.ObserveLoans
import com.agniv.hisaab.core.domain.usecase.SetEmiPaid
import com.agniv.hisaab.core.domain.usecase.canDelete
import com.agniv.hisaab.core.domain.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@Immutable
data class LoansUiState(
    val loading: Boolean = true,
    val overview: LoansOverview? = null,
    val today: LocalDate,
    val viewerUserId: String = "",
    val isAdmin: Boolean = false,
) {
    fun canDelete(loan: Loan): Boolean = canDelete(loan.ownerUserId, viewerUserId, if (isAdmin) Role.ADMIN else Role.MEMBER)
}

sealed interface LoansEvent {
    data class SetPaid(
        val loanId: String,
        val month: YearMonth,
        val paid: Boolean,
    ) : LoansEvent

    data class Delete(
        val loanId: String,
    ) : LoansEvent
}

@HiltViewModel
class LoansViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        members: MemberRepository,
        observeLoans: ObserveLoans,
        private val setEmiPaid: SetEmiPaid,
        private val deleteLoan: DeleteLoan,
        private val time: TimeProvider,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<LoansUiState> =
            sessions.session
                .filterNotNull()
                .flatMapLatest { session ->
                    combine(observeLoans(session), members.observeMembers(session.familyId)) { overview, people ->
                        val admin = people.firstOrNull { it.id == session.memberId }?.role == Role.ADMIN
                        LoansUiState(false, overview, time.today(), session.userId, admin)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LoansUiState(today = time.today()))

        fun onEvent(event: LoansEvent) {
            viewModelScope.launch {
                when (event) {
                    is LoansEvent.SetPaid -> {
                        val session = sessions.session.filterNotNull().first()
                        val loan =
                            state.value.overview
                                ?.items
                                ?.firstOrNull { it.loan.id == event.loanId }
                                ?.loan ?: return@launch
                        setEmiPaid(session, loan, event.month, event.paid)
                    }
                    is LoansEvent.Delete -> deleteLoan(event.loanId)
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
