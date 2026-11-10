package com.agniv.hisaab.feature.udhaar

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Role
import com.agniv.hisaab.core.domain.model.Udhaar
import com.agniv.hisaab.core.domain.model.UdhaarDirection
import com.agniv.hisaab.core.domain.model.UdhaarPayment
import com.agniv.hisaab.core.domain.repository.MemberRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.AddUdhaarPayment
import com.agniv.hisaab.core.domain.usecase.DeleteUdhaar
import com.agniv.hisaab.core.domain.usecase.ObserveUdhaar
import com.agniv.hisaab.core.domain.usecase.RemoveUdhaarPayment
import com.agniv.hisaab.core.domain.usecase.UdhaarItem
import com.agniv.hisaab.core.domain.usecase.UdhaarOverview
import com.agniv.hisaab.core.domain.usecase.canDelete
import com.agniv.hisaab.core.domain.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** null = show everything. */
enum class UdhaarFilter(
    val direction: UdhaarDirection?,
) {
    ALL(null),
    GET(UdhaarDirection.THEY_OWE_ME),
    GIVE(UdhaarDirection.I_OWE),
}

@Immutable
data class UdhaarUiState(
    val loading: Boolean = true,
    val overview: UdhaarOverview = UdhaarOverview(emptyList(), Money.ZERO, Money.ZERO),
    val filter: UdhaarFilter = UdhaarFilter.ALL,
    val viewerUserId: String = "",
    val isAdmin: Boolean = false,
    val today: LocalDate,
) {
    val shown: List<UdhaarItem>
        get() = overview.items.filter { filter.direction == null || it.udhaar.direction == filter.direction }

    fun canDelete(entry: Udhaar): Boolean = canDelete(entry.ownerUserId, viewerUserId, if (isAdmin) Role.ADMIN else Role.MEMBER)
}

sealed interface UdhaarEvent {
    data class SetFilter(
        val filter: UdhaarFilter,
    ) : UdhaarEvent

    data class Pay(
        val entryId: String,
        val amount: Money,
    ) : UdhaarEvent

    data class Settle(
        val entryId: String,
    ) : UdhaarEvent

    data class RemovePayment(
        val payment: UdhaarPayment,
    ) : UdhaarEvent

    data class Delete(
        val entryId: String,
    ) : UdhaarEvent
}

@HiltViewModel
class UdhaarViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        members: MemberRepository,
        observeUdhaar: ObserveUdhaar,
        private val addPayment: AddUdhaarPayment,
        private val removePayment: RemoveUdhaarPayment,
        private val deleteUdhaar: DeleteUdhaar,
        time: TimeProvider,
    ) : ViewModel() {
        private val today = time.today()
        private val filter = MutableStateFlow(UdhaarFilter.ALL)

        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<UdhaarUiState> =
            sessions.session
                .filterNotNull()
                .flatMapLatest { session ->
                    combine(observeUdhaar(session), members.observeMembers(session.familyId), filter) { overview, people, f ->
                        val admin = people.firstOrNull { it.id == session.memberId }?.role == Role.ADMIN
                        UdhaarUiState(false, overview, f, session.userId, admin, today)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UdhaarUiState(today = today))

        fun onEvent(event: UdhaarEvent) {
            viewModelScope.launch {
                when (event) {
                    is UdhaarEvent.SetFilter -> filter.update { event.filter }
                    is UdhaarEvent.Pay -> pay(event.entryId, event.amount)
                    is UdhaarEvent.Settle -> pay(event.entryId, null)
                    is UdhaarEvent.RemovePayment -> removePayment(event.payment)
                    is UdhaarEvent.Delete -> deleteUdhaar(event.entryId)
                }
            }
        }

        /** [amount] null = everything that is left. */
        private suspend fun pay(
            entryId: String,
            amount: Money?,
        ) {
            val session = sessions.session.first() ?: return
            val item =
                state.value.overview.items
                    .firstOrNull { it.udhaar.id == entryId } ?: return
            addPayment(session, item, amount ?: item.remaining)
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
