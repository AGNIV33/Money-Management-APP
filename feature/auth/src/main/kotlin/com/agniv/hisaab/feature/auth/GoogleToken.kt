package com.agniv.hisaab.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

/** Result of the Google account picker: the ID token plus the raw nonce Supabase must check it against. */
internal data class GoogleToken(
    val idToken: String,
    val rawNonce: String,
)

/**
 * Shows the Google account picker via Credential Manager. Returns null if the user cancels or
 * Google isn't available. Needs an Activity context.
 */
internal suspend fun requestGoogleToken(
    context: Context,
    webClientId: String,
): GoogleToken? {
    // Google gets the hash; Supabase gets the raw value and hashes it again to compare.
    val rawNonce = UUID.randomUUID().toString()
    val hashedNonce =
        MessageDigest
            .getInstance("SHA-256")
            .digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }
    val option =
        GetGoogleIdOption
            .Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setNonce(hashedNonce)
            .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val credential = CredentialManager.create(context).getCredential(context, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleToken(GoogleIdTokenCredential.createFrom(credential.data).idToken, rawNonce)
        } else {
            null
        }
    } catch (_: GetCredentialException) {
        null
    }
}
