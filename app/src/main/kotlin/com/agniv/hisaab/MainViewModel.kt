package com.agniv.hisaab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.data.session.LocalSessionRepository
import com.agniv.hisaab.core.domain.repository.AuthRepository
import com.agniv.hisaab.core.domain.repository.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which top-level screen to show. */
enum class RootScreen { SPLASH, LOGIN, HOME }

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val localSession: LocalSessionRepository,
        auth: AuthRepository,
    ) : ViewModel() {
        private val localReady = MutableStateFlow(false)

        val screen: StateFlow<RootScreen> =
            combine(localReady, auth.authState) { ready, authState ->
                when {
                    !ready || authState == AuthState.Loading -> RootScreen.SPLASH
                    authState is AuthState.SignedIn -> RootScreen.HOME
                    else -> RootScreen.LOGIN
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, RootScreen.SPLASH)

        fun start(
            familyName: String,
            memberName: String,
        ) {
            if (localReady.value) return
            viewModelScope.launch {
                localSession.ensureLocalSession(familyName, memberName)
                localReady.value = true
            }
        }
    }
