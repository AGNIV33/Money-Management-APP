package com.agniv.hisaab.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AssistantTokenDto(
    val token: String? = null,
    val model: String? = null,
    val sliceMinutes: Int? = null,
    val error: String? = null,
)

@Serializable
data class PriceRequestDto(
    val product: String,
)

@Serializable
data class PriceAnswerDto(
    val answer: String? = null,
    val error: String? = null,
)

sealed interface AssistantTokenReply {
    data class Granted(
        val token: String,
        val model: String,
        val sliceMinutes: Int,
    ) : AssistantTokenReply

    /** The server said no, with its short code (daily_limit, busy_today, ai_not_configured ...). */
    data class Refused(
        val code: String?,
        val httpStatus: Int,
    ) : AssistantTokenReply
}

sealed interface PriceReply {
    data class Answer(
        val text: String,
    ) : PriceReply

    data class Refused(
        val code: String?,
        val httpStatus: Int,
    ) : PriceReply
}

interface AssistantRemote {
    /** Throws only on network trouble. */
    suspend fun token(): AssistantTokenReply

    suspend fun price(product: String): PriceReply
}

/** Calls `ai-live-token` and `ai-price-lookup` with the signed-in user's token. No AI key is ever in the app. */
@Singleton
class SupabaseAssistantRemote
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) : AssistantRemote {
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        private val client by lazy {
            HttpClient(OkHttp) {
                expectSuccess = false
                install(HttpTimeout) { requestTimeoutMillis = TIMEOUT_MS }
            }
        }

        override suspend fun token(): AssistantTokenReply {
            val response = call("ai-live-token", "{}") ?: return AssistantTokenReply.Refused("not_signed_in", UNAUTHORIZED)
            val body = runCatching { json.decodeFromString(AssistantTokenDto.serializer(), response.bodyAsText()) }.getOrNull()
            val token = body?.token
            val model = body?.model
            return if (response.status.isSuccess() && !token.isNullOrEmpty() && !model.isNullOrEmpty()) {
                AssistantTokenReply.Granted(token, model, body.sliceMinutes ?: DEFAULT_SLICE_MINUTES)
            } else {
                AssistantTokenReply.Refused(body?.error, response.status.value)
            }
        }

        override suspend fun price(product: String): PriceReply {
            val response =
                call("ai-price-lookup", json.encodeToString(PriceRequestDto.serializer(), PriceRequestDto(product)))
                    ?: return PriceReply.Refused("not_signed_in", UNAUTHORIZED)
            val body = runCatching { json.decodeFromString(PriceAnswerDto.serializer(), response.bodyAsText()) }.getOrNull()
            val answer = body?.answer
            return if (response.status.isSuccess() && !answer.isNullOrBlank()) {
                PriceReply.Answer(answer)
            } else {
                PriceReply.Refused(body?.error, response.status.value)
            }
        }

        private suspend fun call(
            function: String,
            body: String,
        ): HttpResponse? {
            val access = supabase.auth.currentAccessTokenOrNull() ?: return null
            return try {
                client.post("${BuildConfig.SUPABASE_URL}/functions/v1/$function") {
                    header("Authorization", "Bearer $access")
                    header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            } catch (e: HttpRequestTimeoutException) {
                throw IOException("timed out", e)
            }
        }

        private companion object {
            const val TIMEOUT_MS = 40_000L
            const val UNAUTHORIZED = 401
            const val DEFAULT_SLICE_MINUTES = 5
        }
    }
