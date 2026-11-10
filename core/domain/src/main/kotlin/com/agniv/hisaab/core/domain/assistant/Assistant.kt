package com.agniv.hisaab.core.domain.assistant

import kotlinx.coroutines.flow.Flow

/** Why the voice assistant could not start. Each one has its own plain message in the app. */
enum class AssistantFailure { NOT_SIGNED_IN, DAILY_LIMIT, BUSY_TODAY, NOT_CONFIGURED, OFFLINE, FAILED }

class AssistantException(
    val failure: AssistantFailure,
) : Exception(failure.name)

/** One thing the assistant asked the phone to look up. [argsJson] is the JSON object text the model sent. */
data class ToolCall(
    val id: String,
    val name: String,
    val argsJson: String,
)

/** The phone's answer to a [ToolCall]. [responseJson] is a JSON object text and holds totals only. */
data class ToolAnswer(
    val id: String,
    val name: String,
    val responseJson: String,
)

/** What comes back from the live conversation, already decoded. */
sealed interface LiveEvent {
    /** The session accepted our setup; the microphone can start. */
    data object Ready : LiveEvent

    /** A piece of the assistant's voice: 16-bit mono PCM at 24 kHz. */
    class Audio(
        val pcm: ByteArray,
    ) : LiveEvent

    /** The user spoke over the assistant: drop whatever audio is still queued. */
    data object Interrupted : LiveEvent

    /** The assistant finished its turn. */
    data object TurnComplete : LiveEvent

    data class ToolCalls(
        val calls: List<ToolCall>,
    ) : LiveEvent

    /** The server will close this connection soon; reconnect with the resume handle. */
    data class GoAway(
        val timeLeftMillis: Long,
    ) : LiveEvent

    /** Handle to continue the same conversation on a new connection. */
    data class Resume(
        val handle: String,
    ) : LiveEvent

    /** The connection ended. [clean] is false when it dropped or the server refused. */
    data class Closed(
        val clean: Boolean,
    ) : LiveEvent
}

/** One live voice connection. Audio in is 16-bit mono PCM at 16 kHz. */
interface LiveSession {
    /** Collect once. Ends after [LiveEvent.Closed]. */
    val events: Flow<LiveEvent>

    /** How long this connection may run before the server cuts it (the minutes reserved for it). */
    val sliceMillis: Long

    fun sendAudio(
        pcm: ByteArray,
        length: Int,
    )

    fun sendToolAnswers(answers: List<ToolAnswer>)

    fun close()
}

interface AssistantRepository {
    /**
     * Gets a short-lived token from the server (which also counts the minutes against the daily limit) and opens
     * the live connection. Pass [resumeHandle] to continue an earlier conversation.
     */
    suspend fun connect(resumeHandle: String? = null): Result<LiveSession>

    /** Current approximate price from the web. Only the product name leaves the phone. */
    suspend fun lookupPrice(product: String): Result<String>
}
