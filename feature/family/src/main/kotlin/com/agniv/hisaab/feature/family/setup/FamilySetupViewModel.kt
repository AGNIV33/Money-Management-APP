package com.agniv.hisaab.feature.family.setup

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.domain.repository.FamilyError
import com.agniv.hisaab.core.domain.repository.FamilyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SetupMode { CREATE, JOIN }

enum class SetupError { NEED_NAME, BAD_CODE, INVALID_CODE, FULL, NETWORK, UNKNOWN }

@Immutable
data class FamilySetupUiState(
    /** True while checking whether this account already has a family on the server. */
    val checking: Boolean = true,
    val mode: SetupMode = SetupMode.CREATE,
    val familyName: String = "",
    val myName: String = "",
    val code: String = "",
    val busy: Boolean = false,
    val error: SetupError? = null,
)

sealed interface SetupEvent {
    data class Mode(
        val mode: SetupMode,
    ) : SetupEvent

    data class FamilyName(
        val value: String,
    ) : SetupEvent

    data class MyName(
        val value: String,
    ) : SetupEvent

    data class Code(
        val value: String,
    ) : SetupEvent

    data object Submit : SetupEvent

    data object Retry : SetupEvent
}

/** Success is seen by the app root (session becomes linked), so this screen only reports errors. */
@HiltViewModel
class FamilySetupViewModel
    @Inject
    constructor(
        private val families: FamilyRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(FamilySetupUiState())
        val state: StateFlow<FamilySetupUiState> = _state.asStateFlow()

        init {
            checkExisting()
        }

        private fun checkExisting() {
            _state.update { it.copy(checking = true, error = null) }
            viewModelScope.launch {
                families
                    .linkExistingFamily()
                    .onSuccess { _state.update { it.copy(checking = false) } }
                    .onFailure { e -> _state.update { it.copy(checking = false, error = e.toSetupError()) } }
            }
        }

        fun onEvent(event: SetupEvent) {
            when (event) {
                is SetupEvent.Mode -> _state.update { it.copy(mode = event.mode, error = null) }
                is SetupEvent.FamilyName -> _state.update { it.copy(familyName = event.value.take(MAX_FAMILY), error = null) }
                is SetupEvent.MyName -> _state.update { it.copy(myName = event.value.take(MAX_NAME), error = null) }
                is SetupEvent.Code ->
                    _state.update {
                        it.copy(
                            code =
                                event.value
                                    .uppercase()
                                    .filter(Char::isLetterOrDigit)
                                    .take(CODE_LENGTH),
                            error = null,
                        )
                    }
                SetupEvent.Retry -> checkExisting()
                SetupEvent.Submit -> submit()
            }
        }

        private fun submit() {
            val s = _state.value
            if (s.busy) return
            val error =
                when {
                    s.myName.isBlank() -> SetupError.NEED_NAME
                    s.mode == SetupMode.CREATE && s.familyName.isBlank() -> SetupError.NEED_NAME
                    s.mode == SetupMode.JOIN && s.code.length != CODE_LENGTH -> SetupError.BAD_CODE
                    else -> null
                }
            if (error != null) {
                _state.update { it.copy(error = error) }
                return
            }
            _state.update { it.copy(busy = true, error = null) }
            viewModelScope.launch {
                val result =
                    when (s.mode) {
                        SetupMode.CREATE -> families.createFamily(s.familyName, s.myName)
                        SetupMode.JOIN -> families.joinFamily(s.code, s.myName)
                    }
                result.onFailure { e -> _state.update { it.copy(busy = false, error = e.toSetupError()) } }
            }
        }

        private fun Throwable.toSetupError(): SetupError =
            when (this) {
                FamilyError.InvalidCode -> SetupError.INVALID_CODE
                FamilyError.Full -> SetupError.FULL
                FamilyError.Network -> SetupError.NETWORK
                else -> SetupError.UNKNOWN
            }

        companion object {
            const val CODE_LENGTH = 6
            private const val MAX_FAMILY = 60
            private const val MAX_NAME = 40
        }
    }
