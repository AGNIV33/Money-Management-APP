package com.agniv.hisaab.feature.assistant

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.domain.assistant.AssistantException
import com.agniv.hisaab.core.domain.assistant.AssistantFailure
import com.agniv.hisaab.core.domain.assistant.AssistantRepository
import com.agniv.hisaab.core.domain.assistant.LiveEvent
import com.agniv.hisaab.core.domain.assistant.LiveSession
import com.agniv.hisaab.feature.assistant.audio.MicRecorder
import com.agniv.hisaab.feature.assistant.audio.VoicePlayer
import com.agniv.hisaab.feature.assistant.tools.AssistantToolRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AssistantPhase { IDLE, CONNECTING, LISTENING, THINKING, SPEAKING, ERROR }

/** What went wrong, or why it stopped. Each has its own plain sentence (en + hi). */
enum class AssistantMessage {
    NO_PERMISSION,
    MIC_FAILED,
    OFFLINE,
    DAILY_LIMIT,
    BUSY_TODAY,
    NOT_CONFIGURED,
    NOT_SIGNED_IN,
    FAILED,
    SESSION_LOST,

    /** Not an error: it stopped by itself after a long silence, to save the free minutes. */
    STOPPED_QUIET,
}

@Immutable
data class AssistantUiState(
    val phase: AssistantPhase = AssistantPhase.IDLE,
    val message: AssistantMessage? = null,
)

/**
 * Runs one voice conversation: opens the live session, streams the microphone up, plays the voice that comes
 * back, answers the assistant's tool calls from this phone, and carries on across the short server sessions
 * (each is a few minutes) without the user noticing. Nothing said or heard is stored (privacy rule).
 *
 * The conversation is a small state machine, so each step is its own short function on purpose.
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class AssistantViewModel
    @Inject
    constructor(
        private val repository: AssistantRepository,
        private val tools: AssistantToolRunner,
    ) : ViewModel() {
        private val _state = MutableStateFlow(AssistantUiState())
        val state: StateFlow<AssistantUiState> = _state.asStateFlow()

        @Volatile private var session: LiveSession? = null

        @Volatile private var ready = false

        @Volatile private var active = false

        @Volatile private var lastVoiceAt = 0L

        @Volatile private var assistantSpeaking = false
        private var resumeHandle: String? = null
        private var reconnecting = false
        private var reconnects = 0
        private var eventsJob: Job? = null
        private var sliceJob: Job? = null
        private var watchdog: Job? = null

        private val player =
            VoicePlayer { speaking ->
                assistantSpeaking = speaking
                if (active) {
                    if (speaking) {
                        setPhase(AssistantPhase.SPEAKING)
                    } else if (_state.value.phase == AssistantPhase.SPEAKING) {
                        setPhase(AssistantPhase.LISTENING)
                    }
                }
            }
        private val mic =
            MicRecorder { buffer, length, loud ->
                if (loud) lastVoiceAt = System.currentTimeMillis()
                // Nothing is sent before the session has accepted its setup.
                if (ready) session?.sendAudio(buffer, length)
            }

        fun start() {
            if (active) return
            active = true
            resumeHandle = null
            reconnects = 0
            _state.value = AssistantUiState(AssistantPhase.CONNECTING)
            viewModelScope.launch {
                repository.connect(null).fold(onSuccess = ::attach, onFailure = { fail(it.toMessage()) })
            }
        }

        fun onPermissionDenied() {
            _state.value = AssistantUiState(AssistantPhase.ERROR, AssistantMessage.NO_PERMISSION)
        }

        /** Ends the conversation and releases the microphone and speaker. Safe to call at any time. */
        fun stop() {
            shutDown()
            _state.value = AssistantUiState()
        }

        private fun shutDown() {
            active = false
            ready = false
            eventsJob?.cancel()
            sliceJob?.cancel()
            watchdog?.cancel()
            mic.stop()
            player.stop()
            session?.close()
            session = null
            assistantSpeaking = false
            reconnecting = false
        }

        private fun fail(message: AssistantMessage) {
            shutDown()
            _state.value = AssistantUiState(AssistantPhase.ERROR, message)
        }

        private fun setPhase(phase: AssistantPhase) = _state.update { if (active) it.copy(phase = phase, message = null) else it }

        /** Makes [next] the live session. The old one (if any) is closed; the conversation carries on via the resume handle. */
        private fun attach(next: LiveSession) {
            if (!active) {
                next.close()
                return
            }
            val old = session
            ready = false
            session = next
            old?.close()
            eventsJob?.cancel()
            eventsJob = viewModelScope.launch { next.events.collect { handle(next, it) } }
            sliceJob?.cancel()
            // Move to a fresh session a little before the server closes this one, so the user never hears a gap.
            sliceJob =
                viewModelScope.launch {
                    delay((next.sliceMillis - SWITCH_BEFORE_MS).coerceAtLeast(MIN_SLICE_MS))
                    if (active && session === next) reconnect()
                }
        }

        private fun handle(
            from: LiveSession,
            event: LiveEvent,
        ) {
            if (!active || session !== from) return
            when (event) {
                LiveEvent.Ready -> onReady()
                is LiveEvent.Audio -> player.enqueue(event.pcm)
                LiveEvent.Interrupted -> {
                    player.flush()
                    setPhase(AssistantPhase.LISTENING)
                }
                LiveEvent.TurnComplete -> Unit
                is LiveEvent.ToolCalls -> answerTools(from, event)
                is LiveEvent.GoAway -> reconnect()
                is LiveEvent.Resume -> resumeHandle = event.handle
                is LiveEvent.Closed -> onClosed(event.clean)
            }
        }

        private fun onReady() {
            ready = true
            reconnecting = false
            if (!mic.isRunning()) {
                player.start()
                if (!mic.start()) {
                    fail(AssistantMessage.MIC_FAILED)
                    return
                }
                lastVoiceAt = System.currentTimeMillis()
                startWatchdog()
            }
            if (!assistantSpeaking) setPhase(AssistantPhase.LISTENING)
        }

        private fun answerTools(
            from: LiveSession,
            event: LiveEvent.ToolCalls,
        ) {
            setPhase(AssistantPhase.THINKING)
            viewModelScope.launch {
                val answers = event.calls.map { tools.answer(it) }
                if (active && session === from) from.sendToolAnswers(answers)
                delay(THINKING_MAX_MS)
                if (_state.value.phase == AssistantPhase.THINKING) setPhase(AssistantPhase.LISTENING)
            }
        }

        private fun onClosed(clean: Boolean) {
            val canContinue = reconnects < MAX_RECONNECTS && (resumeHandle != null || clean)
            if (canContinue) reconnect() else fail(AssistantMessage.SESSION_LOST)
        }

        private fun reconnect() {
            if (!active || reconnecting) return
            reconnecting = true
            reconnects++
            viewModelScope.launch {
                repository.connect(resumeHandle).fold(
                    onSuccess = ::attach,
                    onFailure = { fail(it.toMessage()) },
                )
            }
        }

        /** Ends the session after a long silence so the free minutes are not used up by a phone left listening. */
        private fun startWatchdog() {
            watchdog?.cancel()
            watchdog =
                viewModelScope.launch {
                    while (active) {
                        delay(WATCH_EVERY_MS)
                        val quiet = System.currentTimeMillis() - lastVoiceAt > SILENCE_STOP_MS
                        if (quiet && !assistantSpeaking && _state.value.phase == AssistantPhase.LISTENING) {
                            shutDown()
                            _state.value = AssistantUiState(AssistantPhase.IDLE, AssistantMessage.STOPPED_QUIET)
                        }
                    }
                }
        }

        private fun Throwable.toMessage(): AssistantMessage =
            when ((this as? AssistantException)?.failure) {
                AssistantFailure.NOT_SIGNED_IN -> AssistantMessage.NOT_SIGNED_IN
                AssistantFailure.DAILY_LIMIT -> AssistantMessage.DAILY_LIMIT
                AssistantFailure.BUSY_TODAY -> AssistantMessage.BUSY_TODAY
                AssistantFailure.NOT_CONFIGURED -> AssistantMessage.NOT_CONFIGURED
                AssistantFailure.OFFLINE -> AssistantMessage.OFFLINE
                AssistantFailure.FAILED, null -> AssistantMessage.FAILED
            }

        override fun onCleared() {
            shutDown()
        }

        private companion object {
            const val SWITCH_BEFORE_MS = 20_000L
            const val MIN_SLICE_MS = 20_000L
            const val THINKING_MAX_MS = 12_000L
            const val WATCH_EVERY_MS = 5_000L
            const val SILENCE_STOP_MS = 45_000L
            const val MAX_RECONNECTS = 6
        }
    }
