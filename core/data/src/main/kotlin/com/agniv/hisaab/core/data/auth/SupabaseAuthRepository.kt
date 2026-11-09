package com.agniv.hisaab.core.data.auth

import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.common.result.resultOf
import com.agniv.hisaab.core.domain.repository.AuthError
import com.agniv.hisaab.core.domain.repository.AuthRepository
import com.agniv.hisaab.core.domain.repository.AuthState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private const val HTTP_TOO_MANY = 429

@Singleton
class SupabaseAuthRepository
    @Inject
    constructor(
        private val supabase: SupabaseClient,
        private val dispatchers: DispatcherProvider,
    ) : AuthRepository {
        override val authState: Flow<AuthState> =
            supabase.auth.sessionStatus
                .map { status ->
                    when (status) {
                        is SessionStatus.Authenticated ->
                            status.session.user?.let { AuthState.SignedIn(it.id, it.email) } ?: AuthState.SignedOut
                        is SessionStatus.Initializing -> AuthState.Loading
                        // Offline with an expired token: keep the user in; the token refreshes when online.
                        is SessionStatus.RefreshFailure -> AuthState.Loading
                        is SessionStatus.NotAuthenticated -> AuthState.SignedOut
                    }
                }.distinctUntilChanged()

        override suspend fun sendEmailCode(email: String): Result<Unit> {
            val clean = email.trim().lowercase()
            if (!EMAIL.matches(clean)) return Result.failure(AuthError.InvalidEmail)
            return call {
                supabase.auth.signInWith(OTP) {
                    this.email = clean
                    createUser = true
                }
            }
        }

        override suspend fun verifyEmailCode(
            email: String,
            code: String,
        ): Result<Unit> =
            call {
                supabase.auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email.trim().lowercase(), token = code.trim())
            }

        override suspend fun signInWithGoogle(
            idToken: String,
            rawNonce: String,
        ): Result<Unit> =
            call {
                supabase.auth.signInWith(IDToken) {
                    this.idToken = idToken
                    provider = Google
                    nonce = rawNonce
                }
            }

        override suspend fun signOut() {
            withContext(dispatchers.io) { resultOf { supabase.auth.signOut() } }
        }

        private suspend fun call(block: suspend () -> Unit): Result<Unit> =
            withContext(dispatchers.io) {
                resultOf { block() }.recoverCatching { throw it.toAuthError() }
            }

        private fun Throwable.toAuthError(): Throwable =
            when (this) {
                is HttpRequestException -> AuthError.Network
                is RestException ->
                    if (statusCode == HTTP_TOO_MANY) AuthError.TooManyTries else AuthError.WrongCode
                else -> this
            }
    }
