package com.agniv.hisaab.feature.family.info

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.domain.model.Family
import com.agniv.hisaab.core.domain.model.Member
import com.agniv.hisaab.core.domain.repository.FamilyRepository
import com.agniv.hisaab.core.domain.repository.MemberRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class FamilyUiState(
    val family: Family? = null,
    val members: List<Member> = emptyList(),
    val myMemberId: String? = null,
)

@HiltViewModel
class FamilyViewModel
    @Inject
    constructor(
        sessions: SessionRepository,
        families: FamilyRepository,
        members: MemberRepository,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<FamilyUiState> =
            sessions.session
                .filterNotNull()
                .flatMapLatest { s ->
                    combine(families.observeFamily(s.familyId), members.observeMembers(s.familyId)) { f, m ->
                        FamilyUiState(f, m, s.memberId)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FamilyUiState())

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
