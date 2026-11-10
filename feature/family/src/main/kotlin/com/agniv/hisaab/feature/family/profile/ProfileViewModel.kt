package com.agniv.hisaab.feature.family.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.domain.model.Family
import com.agniv.hisaab.core.domain.model.Member
import com.agniv.hisaab.core.domain.model.Role
import com.agniv.hisaab.core.domain.repository.AccountError
import com.agniv.hisaab.core.domain.repository.AccountRepository
import com.agniv.hisaab.core.domain.repository.AuthRepository
import com.agniv.hisaab.core.domain.repository.AuthState
import com.agniv.hisaab.core.domain.repository.FamilyRepository
import com.agniv.hisaab.core.domain.repository.MemberRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ProfileMessage { NETWORK, NOT_ALLOWED, LAST_ADMIN, BAD_NAME, FAILED }

@Immutable
data class ProfileUiState(
    val family: Family? = null,
    val members: List<Member> = emptyList(),
    val myMemberId: String? = null,
    val email: String? = null,
    val busy: Boolean = false,
    val message: ProfileMessage? = null,
    /** Set when signing out would lose entries that are not uploaded yet. */
    val pendingOnSignOut: Int? = null,
) {
    val me: Member? get() = members.firstOrNull { it.id == myMemberId }
    val isAdmin: Boolean get() = me?.role == Role.ADMIN
}

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        sessions: SessionRepository,
        families: FamilyRepository,
        members: MemberRepository,
        auth: AuthRepository,
        private val account: AccountRepository,
    ) : ViewModel() {
        private val local = MutableStateFlow(ProfileUiState())

        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<ProfileUiState> =
            sessions.session
                .filterNotNull()
                .flatMapLatest { s ->
                    combine(families.observeFamily(s.familyId), members.observeMembers(s.familyId), auth.authState, local) { f, m, a, l ->
                        l.copy(family = f, members = m, myMemberId = s.memberId, email = (a as? AuthState.SignedIn)?.email)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProfileUiState())

        fun rename(name: String) {
            val id = state.value.myMemberId ?: return
            run { account.renameMe(id, name) }
        }

        fun setRole(
            memberId: String,
            role: Role,
        ) = run { account.setRole(memberId, role) }

        fun remove(memberId: String) = run { account.removeMember(memberId) }

        fun clearMessage() = local.update { it.copy(message = null) }

        /** First tap: finds out whether anything would be lost. [force] is true after the user accepts losing it. */
        fun signOut(
            force: Boolean,
            localFamilyName: String,
            localMemberName: String,
            onDone: () -> Unit,
        ) {
            local.update { it.copy(busy = true, message = null, pendingOnSignOut = null) }
            viewModelScope.launch {
                account
                    .signOut(force, localFamilyName, localMemberName)
                    .onSuccess {
                        local.update { it.copy(busy = false) }
                        onDone()
                    }.onFailure { e ->
                        val pending = (e as? AccountError.PendingChanges)?.count
                        local.update {
                            it.copy(
                                busy = false,
                                pendingOnSignOut = pending,
                                message =
                                    if (pending ==
                                        null
                                    ) {
                                        e.toMessage()
                                    } else {
                                        null
                                    },
                            )
                        }
                    }
            }
        }

        fun dismissPending() = local.update { it.copy(pendingOnSignOut = null) }

        private fun run(block: suspend () -> Result<Unit>) {
            local.update { it.copy(busy = true, message = null) }
            viewModelScope.launch {
                val result = block()
                local.update { it.copy(busy = false, message = result.exceptionOrNull()?.toMessage()) }
            }
        }

        private fun Throwable.toMessage(): ProfileMessage =
            when (this) {
                AccountError.Network -> ProfileMessage.NETWORK
                AccountError.NotAllowed -> ProfileMessage.NOT_ALLOWED
                AccountError.LastAdmin -> ProfileMessage.LAST_ADMIN
                AccountError.BadName -> ProfileMessage.BAD_NAME
                else -> ProfileMessage.FAILED
            }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
