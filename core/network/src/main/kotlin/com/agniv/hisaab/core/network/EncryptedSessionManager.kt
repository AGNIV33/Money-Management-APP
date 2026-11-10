package com.agniv.hisaab.core.network

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM with a random IV per message: output is IV + ciphertext + tag, so any change to the
 * stored text makes [decrypt] throw instead of returning garbage. Key comes from outside so tests can
 * use a plain software key.
 */
class TokenCipher(
    private val key: () -> SecretKey,
) {
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray): ByteArray {
        if (data.size <= IV_BYTES) throw GeneralSecurityException("too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES))
        return cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val KEY_BITS = 256
        private const val KEYSTORE = "AndroidKeyStore"

        /** Key that never leaves the phone's secure hardware (or the OS keystore on phones without it). */
        fun androidKeystoreKey(alias: String): SecretKey {
            val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (store.getKey(alias, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(
                KeyGenParameterSpec
                    .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build(),
            )
            return generator.generateKey()
        }
    }
}

/**
 * Keeps the login (access + refresh token) encrypted on the phone (IMPLEMENTATION_PLAN 2.5).
 * supabase-kt's default stores it as plain JSON in shared preferences; a refresh token is enough to
 * act as the user, so it is the one local secret worth hardware-backed encryption.
 * Older installs are moved over on first read so nobody gets logged out.
 */
class EncryptedSessionManager(
    private val prefs: SharedPreferences,
    private val legacyPrefs: SharedPreferences,
    private val cipher: TokenCipher,
) : SessionManager {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) =
        withContext(Dispatchers.IO) {
            val bytes = cipher.encrypt(json.encodeToString(UserSession.serializer(), session).toByteArray())
            prefs.edit().putString(KEY, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
        }

    // supabase-kt calls loadSessionOrNull(); loadSession() must throw when there is none.
    override suspend fun loadSession(): UserSession = loadSessionOrNull() ?: error("no saved session")

    override suspend fun loadSessionOrNull(): UserSession? =
        withContext(Dispatchers.IO) {
            val stored = prefs.getString(KEY, null)
            if (stored == null) return@withContext migrateLegacy()
            runCatching {
                val plain = cipher.decrypt(Base64.decode(stored, Base64.NO_WRAP))
                json.decodeFromString(UserSession.serializer(), plain.decodeToString())
            }.getOrElse {
                // Key gone (e.g. data restored to a new phone) or text damaged: ask for login again.
                prefs.edit().remove(KEY).apply()
                null
            }
        }

    override suspend fun deleteSession() =
        withContext(Dispatchers.IO) {
            prefs.edit().remove(KEY).apply()
            legacyPrefs.edit().remove(LEGACY_KEY).apply()
        }

    private suspend fun migrateLegacy(): UserSession? {
        val text = legacyPrefs.getString(LEGACY_KEY, null) ?: return null
        val session = runCatching { json.decodeFromString(UserSession.serializer(), text) }.getOrNull()
        session?.let { saveSession(it) }
        legacyPrefs.edit().remove(LEGACY_KEY).apply()
        return session
    }

    companion object {
        private const val KEY = "session"
        private const val PREFS = "secure_session"
        private const val KEY_ALIAS = "kinly_session_key"

        // Where supabase-kt's default SettingsSessionManager kept it (default shared preferences, key "session").
        private const val LEGACY_KEY = "session"

        fun create(context: Context): EncryptedSessionManager =
            EncryptedSessionManager(
                prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
                legacyPrefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE),
                cipher = TokenCipher { TokenCipher.androidKeystoreKey(KEY_ALIAS) },
            )
    }
}
