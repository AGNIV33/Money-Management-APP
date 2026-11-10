package com.agniv.hisaab.core.data.assistant

import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.domain.assistant.AssistantException
import com.agniv.hisaab.core.domain.assistant.AssistantFailure
import com.agniv.hisaab.core.domain.assistant.AssistantRepository
import com.agniv.hisaab.core.domain.assistant.LiveSession
import com.agniv.hisaab.core.network.AssistantRemote
import com.agniv.hisaab.core.network.AssistantTokenReply
import com.agniv.hisaab.core.network.PriceReply
import com.agniv.hisaab.core.network.live.LiveConnector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

private const val MILLIS_PER_MINUTE = 60_000L

class DefaultAssistantRepository
    @Inject
    constructor(
        private val remote: AssistantRemote,
        private val connector: LiveConnector,
        private val dispatchers: DispatcherProvider,
    ) : AssistantRepository {
        @Suppress("TooGenericExceptionCaught")
        override suspend fun connect(resumeHandle: String?): Result<LiveSession> =
            withContext(dispatchers.io) {
                try {
                    // The WebSocket upgrade can fail with several unrelated exception types, hence the broad catch below.
                    when (val reply = remote.token()) {
                        is AssistantTokenReply.Granted ->
                            Result.success(
                                connector.open(reply.token, reply.model, resumeHandle, reply.sliceMinutes * MILLIS_PER_MINUTE),
                            )
                        is AssistantTokenReply.Refused -> Result.failure(AssistantException(reply.code.toFailure()))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Result.failure(AssistantException(AssistantFailure.OFFLINE).also { it.initCause(e) })
                } catch (e: Exception) {
                    Result.failure(AssistantException(AssistantFailure.FAILED).also { it.initCause(e) })
                }
            }

        override suspend fun lookupPrice(product: String): Result<String> =
            withContext(dispatchers.io) {
                try {
                    when (val reply = remote.price(product)) {
                        is PriceReply.Answer -> Result.success(reply.text)
                        is PriceReply.Refused -> Result.failure(AssistantException(reply.code.toFailure()))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Result.failure(AssistantException(AssistantFailure.OFFLINE).also { it.initCause(e) })
                }
            }

        private fun String?.toFailure(): AssistantFailure =
            when (this) {
                "daily_limit" -> AssistantFailure.DAILY_LIMIT
                "busy_today" -> AssistantFailure.BUSY_TODAY
                "ai_not_configured" -> AssistantFailure.NOT_CONFIGURED
                "not_signed_in" -> AssistantFailure.NOT_SIGNED_IN
                else -> AssistantFailure.FAILED
            }
    }
