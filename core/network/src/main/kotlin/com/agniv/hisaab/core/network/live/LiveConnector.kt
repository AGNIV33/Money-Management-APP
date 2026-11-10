package com.agniv.hisaab.core.network.live

import com.agniv.hisaab.core.domain.assistant.LiveEvent
import com.agniv.hisaab.core.domain.assistant.LiveSession
import com.agniv.hisaab.core.domain.assistant.ToolAnswer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val ENDPOINT =
    "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContentConstrained"
private const val PING_MS = 20_000L
private const val OUTBOX_SIZE = 256
private const val CLOSE_WAIT_MS = 1_000L

/** Opens the live voice WebSocket with a short-lived token. The token is never logged. */
@Singleton
class LiveConnector
    @Inject
    constructor() {
        private val client by lazy {
            HttpClient(OkHttp) {
                install(WebSockets) { pingIntervalMillis = PING_MS }
            }
        }

        /** Throws on network trouble. */
        suspend fun open(
            token: String,
            model: String,
            resumeHandle: String?,
            sliceMillis: Long,
        ): LiveSession {
            val socket = client.webSocketSession(urlString = "$ENDPOINT?access_token=$token")
            val session = KtorLiveSession(socket, sliceMillis)
            session.start(LiveProtocol.setupMessage(model, resumeHandle))
            return session
        }
    }

private class KtorLiveSession(
    private val socket: DefaultClientWebSocketSession,
    override val sliceMillis: Long,
) : LiveSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inbox = Channel<LiveEvent>(Channel.UNLIMITED)
    private val outbox = Channel<String>(OUTBOX_SIZE)

    override val events: Flow<LiveEvent> = inbox.receiveAsFlow()

    fun start(setup: String) {
        outbox.trySend(setup)
        scope.launch {
            try {
                for (message in outbox) socket.send(Frame.Text(message))
            } catch (_: Exception) {
                // The reader below reports the closed connection.
            }
        }
        scope.launch { read() }
    }

    private suspend fun read() {
        var failed = false
        try {
            for (frame in socket.incoming) {
                val text =
                    when (frame) {
                        is Frame.Text -> frame.readText()
                        is Frame.Binary -> String(frame.data, Charsets.UTF_8)
                        else -> continue
                    }
                LiveProtocol.parse(text).forEach { inbox.trySend(it) }
            }
        } catch (_: Exception) {
            failed = true
        }
        val reason = withTimeoutOrNull(CLOSE_WAIT_MS) { socket.closeReason.await() }
        inbox.trySend(LiveEvent.Closed(clean = !failed && reason?.code == CloseReason.Codes.NORMAL.code))
        inbox.close()
        outbox.close()
    }

    override fun sendAudio(
        pcm: ByteArray,
        length: Int,
    ) {
        // Dropping a 100 ms piece on a full queue is better than blocking the microphone thread.
        outbox.trySend(LiveProtocol.audioMessage(pcm, length))
    }

    override fun sendToolAnswers(answers: List<ToolAnswer>) {
        outbox.trySend(LiveProtocol.toolAnswersMessage(answers))
    }

    override fun close() {
        outbox.close()
        scope.launch {
            runCatching { socket.close() }
            scope.cancel()
        }
    }
}
