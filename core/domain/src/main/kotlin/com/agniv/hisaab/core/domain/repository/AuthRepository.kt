package com.agniv.hisaab.core.domain.repository

import kotlinx.coroutines.flow.Flow

sealed interface AuthState {
    /** Restoring a saved login from disk; show the splash, not the login screen. */
    data object Loading : AuthState

    data object SignedOut : AuthState

    data class SignedIn(
        val userId: String,
        val email: String?,
    ) : AuthState
}

sealed class AuthError(
    message: String,
) : Exception(message) {
    data object InvalidEmail : AuthError("Invalid email")

    data object WrongCode : AuthError("Wrong or expired code")

    data object TooManyTries : AuthError("Too many tries, wait a bit")

    data object Network : AuthError("No internet")
}

interface AuthRepository {
    val authState: Flow<AuthState>

    /** Emails a 6-digit code; creates the account on first use. */
    suspend fun sendEmailCode(email: String): Result<Unit>

    suspend fun verifyEmailCode(
        email: String,
        code: String,
    ): Result<Unit>

    /** Google ID token from Credential Manager; nonce is the raw value whose hash went to Google. */
    suspend fun signInWithGoogle(
        idToken: String,
        rawNonce: String,
    ): Result<Unit>

    suspend fun signOut()
}
