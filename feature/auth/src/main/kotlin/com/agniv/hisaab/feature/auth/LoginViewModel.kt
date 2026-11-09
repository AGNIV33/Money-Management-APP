package com.agniv.hisaab.feature.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.domain.repository.AuthError
import com.agniv.hisaab.core.domain.repository.AuthRepository
import com.agniv.hisaab.core.network.GoogleWebClientId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoginStep { EMAIL, CODE }

enum class LoginError { INVALID_EMAIL, WRONG_CODE, TOO_MANY, NETWORK, GOOGLE_FAILED, UNKNOWN }

@Immutable
data class LoginUiState(
    val step: LoginStep = LoginStep.EMAIL,
    val email: String = "",
    val code: String = "",
    val busy: Boolean = false,
    val error: LoginError? = null,
    val googleAvailable: Boolean = false,
)

sealed interface LoginEvent {
    data class EmailChanged(
        val value: String,
    ) : LoginEvent

    data class CodeChanged(
        val value: String,
    ) : LoginEvent

    data object SendCode : LoginEvent

    data object Verify : LoginEvent

    data object ChangeEmail : LoginEvent

    data class GoogleToken(
        val idToken: String,
        val rawNonce: String,
    ) : LoginEvent

    data object GoogleFailed : LoginEvent
}

/** Login succeeds by changing AuthRepository.authState; the app's root reacts to that, not to this screen. */
@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        @GoogleWebClientId val googleClientId: String,
    ) : ViewModel() {
        private val _state = MutableStateFlow(LoginUiState(googleAvailable = googleClientId.isNotBlank()))
        val state: StateFlow<LoginUiState> = _state.asStateFlow()

        fun onEvent(event: LoginEvent) {
            when (event) {
                is LoginEvent.EmailChanged -> _state.update { it.copy(email = event.value, error = null) }
                is LoginEvent.CodeChanged ->
                    _state.update { it.copy(code = event.value.filter(Char::isDigit).take(CODE_LENGTH), error = null) }
                LoginEvent.ChangeEmail -> _state.update { it.copy(step = LoginStep.EMAIL, code = "", error = null) }
                LoginEvent.SendCode ->
                    launchAuth(onSuccess = { it.copy(step = LoginStep.CODE) }) { auth.sendEmailCode(_state.value.email) }
                LoginEvent.Verify -> launchAuth { auth.verifyEmailCode(_state.value.email, _state.value.code) }
                is LoginEvent.GoogleToken -> launchAuth { auth.signInWithGoogle(event.idToken, event.rawNonce) }
                LoginEvent.GoogleFailed -> _state.update { it.copy(error = LoginError.GOOGLE_FAILED) }
            }
        }

        private fun launchAuth(
            onSuccess: (LoginUiState) -> LoginUiState = { it },
            block: suspend () -> Result<Unit>,
        ) {
            if (_state.value.busy) return
            _state.update { it.copy(busy = true, error = null) }
            viewModelScope.launch {
                block()
                    .onSuccess { _state.update { onSuccess(it).copy(busy = false) } }
                    .onFailure { e -> _state.update { it.copy(busy = false, error = e.toLoginError()) } }
            }
        }

        private fun Throwable.toLoginError(): LoginError =
            when (this) {
                AuthError.InvalidEmail -> LoginError.INVALID_EMAIL
                AuthError.WrongCode -> LoginError.WRONG_CODE
                AuthError.TooManyTries -> LoginError.TOO_MANY
                AuthError.Network -> LoginError.NETWORK
                else -> LoginError.UNKNOWN
            }

        companion object {
            const val CODE_LENGTH = 6
        }
    }
