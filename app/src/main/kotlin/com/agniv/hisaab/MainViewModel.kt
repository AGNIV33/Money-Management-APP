package com.agniv.hisaab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.data.session.LocalSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val localSession: LocalSessionRepository,
    ) : ViewModel() {
        private val _ready = MutableStateFlow(false)
        val ready: StateFlow<Boolean> = _ready.asStateFlow()

        fun start(
            familyName: String,
            memberName: String,
        ) {
            if (_ready.value) return
            viewModelScope.launch {
                localSession.ensureLocalSession(familyName, memberName)
                _ready.value = true
            }
        }
    }
