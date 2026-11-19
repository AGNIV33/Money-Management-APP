package com.agniv.hisaab.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data object UpToDate : UpdateState

    data class Available(
        val info: UpdateInfo,
    ) : UpdateState

    /** [percent] is 0..100, or -1 when the size is unknown. */
    data class Downloading(
        val info: UpdateInfo,
        val percent: Int,
    ) : UpdateState

    /** Downloaded; Android first needs "Allow from this source" for Kinly. */
    data class NeedsPermission(
        val info: UpdateInfo,
        val apk: File,
    ) : UpdateState

    data object Failed : UpdateState
}

/** Settings > App updates. Nothing happens in the background: every step starts from a tap (or opening Settings). */
class UpdateViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val updater = AppUpdater(app)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Quiet check when Settings opens: only shows something if an update exists. */
    fun checkQuietly() {
        if (_state.value != UpdateState.Idle) return
        viewModelScope.launch {
            updater.check(BuildConfig.VERSION_NAME).onSuccess { info ->
                if (info != null && _state.value == UpdateState.Idle) _state.value = UpdateState.Available(info)
            }
        }
    }

    /** Settings row tap: shows what is known, or checks again. */
    fun onRowTap() {
        val now = _state.value
        if (now !is UpdateState.Available && now !is UpdateState.Downloading) check()
    }

    fun check() {
        val now = _state.value
        if (now is UpdateState.Checking || now is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            _state.value =
                updater.check(BuildConfig.VERSION_NAME).fold(
                    onSuccess = { info -> if (info == null) UpdateState.UpToDate else UpdateState.Available(info) },
                    onFailure = { UpdateState.Failed },
                )
        }
    }

    fun download(info: UpdateInfo) {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(info, 0)
        viewModelScope.launch {
            updater
                .download(info) { percent -> _state.value = UpdateState.Downloading(info, percent) }
                .fold(
                    onSuccess = { apk -> installOrAsk(info, apk) },
                    onFailure = { _state.value = UpdateState.Failed },
                )
        }
    }

    /** After the user comes back from the "Allow from this source" screen. */
    fun retryInstall() {
        val now = _state.value as? UpdateState.NeedsPermission ?: return
        installOrAsk(now.info, now.apk)
    }

    fun openPermission() = runCatching { updater.openInstallPermission() }

    fun dismiss() {
        if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Idle
    }

    private fun installOrAsk(
        info: UpdateInfo,
        apk: File,
    ) {
        if (!updater.canInstall()) {
            _state.value = UpdateState.NeedsPermission(info, apk)
            return
        }
        _state.value = runCatching { updater.install(apk) }.fold({ UpdateState.Available(info) }, { UpdateState.Failed })
    }
}
