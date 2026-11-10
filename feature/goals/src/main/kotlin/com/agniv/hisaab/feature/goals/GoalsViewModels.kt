package com.agniv.hisaab.feature.goals

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Goal
import com.agniv.hisaab.core.domain.model.GoalContribution
import com.agniv.hisaab.core.domain.model.Role
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.repository.GoalRepository
import com.agniv.hisaab.core.domain.repository.MemberRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.AddGoalSavings
import com.agniv.hisaab.core.domain.usecase.DeleteGoal
import com.agniv.hisaab.core.domain.usecase.GoalDraft
import com.agniv.hisaab.core.domain.usecase.GoalError
import com.agniv.hisaab.core.domain.usecase.GoalsOverview
import com.agniv.hisaab.core.domain.usecase.ObserveGoals
import com.agniv.hisaab.core.domain.usecase.RemoveGoalSavings
import com.agniv.hisaab.core.domain.usecase.SaveGoal
import com.agniv.hisaab.core.domain.usecase.canDelete
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class GoalsUiState(
    val loading: Boolean = true,
    val overview: GoalsOverview = GoalsOverview(emptyList(), Money.ZERO, Money.ZERO),
    val viewerUserId: String = "",
    val isAdmin: Boolean = false,
) {
    fun canDelete(goal: Goal): Boolean = canDelete(goal.ownerUserId, viewerUserId, if (isAdmin) Role.ADMIN else Role.MEMBER)
}

sealed interface GoalsEvent {
    data class Add(
        val goalId: String,
        val amount: Money,
    ) : GoalsEvent

    data class Remove(
        val entry: GoalContribution,
    ) : GoalsEvent

    data class Delete(
        val goalId: String,
    ) : GoalsEvent
}

@HiltViewModel
class GoalsViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        members: MemberRepository,
        observeGoals: ObserveGoals,
        private val addSavings: AddGoalSavings,
        private val removeSavings: RemoveGoalSavings,
        private val deleteGoal: DeleteGoal,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<GoalsUiState> =
            sessions.session
                .filterNotNull()
                .flatMapLatest { session ->
                    combine(observeGoals(session), members.observeMembers(session.familyId)) { overview, people ->
                        val admin = people.firstOrNull { it.id == session.memberId }?.role == Role.ADMIN
                        GoalsUiState(false, overview, session.userId, admin)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GoalsUiState())

        fun onEvent(event: GoalsEvent) {
            viewModelScope.launch {
                when (event) {
                    is GoalsEvent.Add -> {
                        val session = sessions.session.first() ?: return@launch
                        val goal =
                            state.value.overview.items
                                .firstOrNull { it.goal.id == event.goalId }
                                ?.goal ?: return@launch
                        addSavings(session, goal, event.amount)
                    }
                    is GoalsEvent.Remove -> removeSavings(event.entry)
                    is GoalsEvent.Delete -> deleteGoal(event.goalId)
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

enum class GoalField { NAME, TARGET, DEADLINE, SAVE }

@Immutable
data class AddGoalUiState(
    val editingId: String? = null,
    val name: String = "",
    val target: String = "",
    val deadline: LocalDate? = null,
    val onlyMe: Boolean = false,
    val error: GoalField? = null,
    val saving: Boolean = false,
)

@HiltViewModel
class AddGoalViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        private val repository: GoalRepository,
        private val saveGoal: SaveGoal,
    ) : ViewModel() {
        private val _state = MutableStateFlow(AddGoalUiState())
        val state: StateFlow<AddGoalUiState> = _state
        private val saved = Channel<Unit>(Channel.CONFLATED)
        val savedEvents = saved.receiveAsFlow()
        private var started = false

        fun start(editId: String?) {
            if (started || editId == null) return
            started = true
            viewModelScope.launch {
                val g = repository.get(editId) ?: return@launch
                _state.update {
                    it.copy(
                        editingId = g.id,
                        name = g.name,
                        target = amountText(g.target),
                        deadline = g.deadline,
                        onlyMe = g.visibility == Visibility.PRIVATE,
                    )
                }
            }
        }

        fun setName(text: String) = _state.update { it.copy(name = text, error = null) }

        fun setTarget(text: String) = _state.update { it.copy(target = text, error = null) }

        fun setDeadline(date: LocalDate?) = _state.update { it.copy(deadline = date, error = null) }

        fun setOnlyMe(on: Boolean) = _state.update { it.copy(onlyMe = on) }

        fun save() {
            val s = _state.value
            if (s.saving) return
            val target = Money.parse(s.target)?.takeIf { it.paise > 0 }
            if (target == null) {
                _state.update { it.copy(error = GoalField.TARGET) }
                return
            }
            _state.update { it.copy(saving = true) }
            viewModelScope.launch {
                val session = sessions.session.filterNotNull().first()
                val draft = GoalDraft(s.name, target, s.deadline, if (s.onlyMe) Visibility.PRIVATE else Visibility.FAMILY)
                saveGoal(session, draft, s.editingId)
                    .onSuccess { saved.send(Unit) }
                    .onFailure { e -> _state.update { it.copy(saving = false, error = e.toField()) } }
            }
        }

        private fun Throwable.toField(): GoalField =
            when (this) {
                GoalError.NoName, GoalError.NameTooLong -> GoalField.NAME
                GoalError.BadTarget -> GoalField.TARGET
                GoalError.DeadlinePassed -> GoalField.DEADLINE
                else -> GoalField.SAVE
            }

        private fun amountText(money: Money): String =
            if (money.paisePart == 0L) "${money.rupeesPart}" else "${money.rupeesPart}.${money.paisePart.toString().padStart(2, '0')}"
    }
