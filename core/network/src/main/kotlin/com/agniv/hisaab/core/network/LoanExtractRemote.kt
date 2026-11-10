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
data class LoanExtractRequestDto(
    val text: String,
)

@Serializable
data class LoanFieldsDto(
    val loanType: String? = null,
    val lender: String? = null,
    val principalRupees: Long? = null,
    val annualRatePercent: Double? = null,
    val tenureMonths: Int? = null,
    val emiRupees: Long? = null,
    val firstEmiDate: String? = null,
)

@Serializable
data class LoanExtractResponseDto(
    val loan: LoanFieldsDto? = null,
    val error: String? = null,
)

sealed interface LoanExtractReply {
    data class Found(
        val fields: LoanFieldsDto,
    ) : LoanExtractReply

    /** The server said no, with its short code (daily_limit, nothing_found, not_masked ...). */
    data class Refused(
        val code: String?,
        val httpStatus: Int,
    ) : LoanExtractReply
}

interface LoanExtractRemote {
    /** Throws only on network trouble. */
    suspend fun extract(maskedText: String): LoanExtractReply
}

/** Calls the `ai-loan-extract` Edge Function with the signed-in user's token. Only masked text is sent. */
@Singleton
class SupabaseLoanExtractRemote
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) : LoanExtractRemote {
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

        override suspend fun extract(maskedText: String): LoanExtractReply {
            val token = supabase.auth.currentAccessTokenOrNull() ?: return LoanExtractReply.Refused("not_signed_in", UNAUTHORIZED)
            val response =
                try {
                    client.post("${BuildConfig.SUPABASE_URL}/functions/v1/ai-loan-extract") {
                        header("Authorization", "Bearer $token")
                        header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        contentType(ContentType.Application.Json)
                        setBody(json.encodeToString(LoanExtractRequestDto.serializer(), LoanExtractRequestDto(maskedText)))
                    }
                } catch (e: HttpRequestTimeoutException) {
                    throw IOException("timed out", e)
                }
            val body = runCatching { json.decodeFromString(LoanExtractResponseDto.serializer(), response.bodyAsText()) }.getOrNull()
            val fields = body?.loan
            return if (response.status.isSuccess() && fields != null) {
                LoanExtractReply.Found(fields)
            } else {
                LoanExtractReply.Refused(body?.error, response.status.value)
            }
        }

        private companion object {
            const val TIMEOUT_MS = 40_000L
            const val UNAUTHORIZED = 401
        }
    }
