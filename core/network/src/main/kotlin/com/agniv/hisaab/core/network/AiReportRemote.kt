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
data class AiCategoryDto(
    val name: String,
    val thisPaise: Long,
    val lastPaise: Long,
)

/** Totals only, by design: there is no field for a name, note, phone or account number. */
@Serializable
data class AiReportRequestDto(
    val familyId: String,
    val month: String,
    val language: String,
    val totalPaise: Long,
    val previousTotalPaise: Long,
    val budgetPaise: Long?,
    val emiSharePercent: Int?,
    val categories: List<AiCategoryDto>,
)

@Serializable
data class AiTipDto(
    val title: String,
    val detail: String,
    val estSavingPaise: Long = 0,
)

@Serializable
data class AiReportResponseDto(
    val tips: List<AiTipDto> = emptyList(),
    val error: String? = null,
)

/** Outcome of the call, before the data layer turns it into a domain failure. */
sealed interface AiReportReply {
    data class Tips(
        val tips: List<AiTipDto>,
    ) : AiReportReply

    /** The server said no, with its short code (ai_not_configured, ai_failed, not_allowed ...). */
    data class Refused(
        val code: String?,
        val httpStatus: Int,
    ) : AiReportReply
}

interface AiReportRemote {
    /** Throws only on network trouble (no internet, timeout). */
    suspend fun fetch(request: AiReportRequestDto): AiReportReply
}

/** Calls the `ai-monthly-report` Edge Function with the signed-in user's token. The AI key is never in the app. */
@Singleton
class SupabaseAiReportRemote
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) : AiReportRemote {
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        private val client by lazy {
            HttpClient(OkHttp) {
                expectSuccess = false
                install(HttpTimeout) {
                    requestTimeoutMillis = TIMEOUT_MS
                }
            }
        }

        override suspend fun fetch(request: AiReportRequestDto): AiReportReply {
            val token = supabase.auth.currentAccessTokenOrNull() ?: return AiReportReply.Refused("not_signed_in", UNAUTHORIZED)
            val response =
                try {
                    client.post("${BuildConfig.SUPABASE_URL}/functions/v1/ai-monthly-report") {
                        header("Authorization", "Bearer $token")
                        header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        contentType(ContentType.Application.Json)
                        setBody(json.encodeToString(AiReportRequestDto.serializer(), request))
                    }
                } catch (e: HttpRequestTimeoutException) {
                    // Same meaning as no connection for the app: try again later.
                    throw IOException("timed out", e)
                }
            val body = runCatching { json.decodeFromString(AiReportResponseDto.serializer(), response.bodyAsText()) }.getOrNull()
            return if (response.status.isSuccess() && body != null && body.tips.isNotEmpty()) {
                AiReportReply.Tips(body.tips)
            } else {
                AiReportReply.Refused(body?.error, response.status.value)
            }
        }

        private companion object {
            const val TIMEOUT_MS = 40_000L
            const val UNAUTHORIZED = 401
        }
    }
