package com.agniv.hisaab.core.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EncryptedSessionManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences("secure_test", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("legacy_test", Context.MODE_PRIVATE)

    // Robolectric has no AndroidKeyStore; a software AES key exercises the same cipher code.
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = TokenCipher { key }
    private val manager = EncryptedSessionManager(prefs, legacy, cipher)

    private val sessionJson = """{"access_token":"ACCESS-123","refresh_token":"REFRESH-456","expires_in":3600,"token_type":"bearer"}"""
    private val session = Json { ignoreUnknownKeys = true }.decodeFromString(UserSession.serializer(), sessionJson)

    @Test
    fun `saved session reads back but is not readable on disk`() =
        runTest {
            manager.saveSession(session)
            val stored = prefs.getString("session", null).orEmpty()
            assertThat(stored).isNotEmpty()
            assertThat(stored).doesNotContain("REFRESH-456")
            assertThat(manager.loadSessionOrNull()?.refreshToken).isEqualTo("REFRESH-456")
        }

    @Test
    fun `old plain session is moved over and the plain copy removed`() =
        runTest {
            legacy.edit().putString("session", sessionJson).commit()
            assertThat(manager.loadSessionOrNull()?.accessToken).isEqualTo("ACCESS-123")
            assertThat(legacy.getString("session", null)).isNull()
            assertThat(prefs.getString("session", null)).doesNotContain("ACCESS-123")
        }

    @Test
    fun `damaged or foreign data means log in again, not a crash`() =
        runTest {
            prefs.edit().putString("session", "bm90IGVuY3J5cHRlZA==").commit()
            assertThat(manager.loadSessionOrNull()).isNull()
            assertThat(prefs.getString("session", null)).isNull()
        }

    @Test
    fun `delete clears both places`() =
        runTest {
            manager.saveSession(session)
            legacy.edit().putString("session", sessionJson).commit()
            manager.deleteSession()
            assertThat(manager.loadSessionOrNull()).isNull()
        }

    @Test(expected = GeneralSecurityException::class)
    fun `tampered ciphertext is rejected`() {
        val data = cipher.encrypt("hello".toByteArray())
        data[data.size - 1] = (data.last() + 1).toByte()
        cipher.decrypt(data)
    }
}
