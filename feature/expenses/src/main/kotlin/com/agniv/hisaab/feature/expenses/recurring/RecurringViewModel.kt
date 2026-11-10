package com.agniv.hisaab.feature.expenses.recurring

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.model.Recurring
import com.agniv.hisaab.core.domain.model.Role
import com.agniv.hisaab.core.domain.repository.CategoryRepository
import com.agniv.hisaab.core.domain.repository.MemberRepository
import com.agniv.hisaab.core.domain.repository.RecurringRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.DeleteRecurring
import com.agniv.hisaab.core.domain.usecase.canDelete
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class RecurringUiState(
    val items: List<Recurring> = emptyList(),
    val categories: Map<String, Category> = emptyMap(),
    val viewerUserId: String = "",
    val isAdmin: Boolean = false,
) {
    fun canDelete(item: Recurring): Boolean = canDelete(item.ownerUserId, viewerUserId, if (isAdmin) Role.ADMIN else Role.MEMBER)
}

@HiltViewModel
class RecurringViewModel
    @Inject
    constructor(
        sessions: SessionRepository,
        recurring: RecurringRepository,
        categories: CategoryRepository,
        members: MemberRepository,
        private val deleteRecurring: DeleteRecurring,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<RecurringUiState> =
            sessions.session
                .filterNotNull()
                .flatMapLatest { session ->
                    combine(
                        recurring.observe(session.familyId, session.userId),
                        categories.observeCategories(session.familyId),
                        members.observeMembers(session.familyId),
                    ) { items, cats, people ->
                        val admin = people.firstOrNull { it.id == session.memberId }?.role == Role.ADMIN
                        RecurringUiState(items, cats.associateBy { it.id }, session.userId, admin)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RecurringUiState())

        fun delete(id: String) {
            viewModelScope.launch { deleteRecurring(id) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
