package com.agniv.hisaab.lock

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** What happened when a PIN was tried. */
sealed interface PinCheck {
    data object Ok : PinCheck

    data class Wrong(
        val triesLeft: Int,
    ) : PinCheck

    /** Too many wrong tries: the keypad waits until [untilMillis] (wall clock). */
    data class LockedOut(
        val untilMillis: Long,
    ) : PinCheck
}

/**
 * Counting wrong PINs (board: 5 wrong → wait 30 seconds). Pure, so it is unit-tested without a phone.
 */
class PinAttempts(
    var wrong: Int = 0,
    var lockedUntil: Long = 0L,
) {
    fun lockedAt(now: Long): Boolean = now < lockedUntil

    fun onWrong(now: Long): PinCheck {
        wrong++
        if (wrong >= MAX_WRONG) {
            wrong = 0
            lockedUntil = now + LOCKOUT_MS
            return PinCheck.LockedOut(lockedUntil)
        }
        return PinCheck.Wrong(MAX_WRONG - wrong)
    }

    fun onRight() {
        wrong = 0
        lockedUntil = 0L
    }

    companion object {
        const val MAX_WRONG = 5
        const val LOCKOUT_MS = 30_000L
        const val PIN_LENGTH = 4
    }
}

/**
 * The app-only 4-digit PIN. Only a salted PBKDF2 hash is kept, in this phone's private app storage; it is never
 * synced or sent anywhere, and the PIN itself is never stored. Wrong tries survive a restart, so killing the app
 * does not skip the 30-second wait.
 */
class PinStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    val hasPin: Boolean get() = prefs.contains(KEY_HASH)

    /** Unlock with the fingerprint as well as the PIN (board "Use Fingerprint"). */
    var useFingerprint: Boolean
        get() = prefs.getBoolean(KEY_FINGER, true)
        set(value) = prefs.edit().putBoolean(KEY_FINGER, value).apply()

    val lockedUntil: Long get() = prefs.getLong(KEY_UNTIL, 0L)

    /** Slow on purpose (about 0.1 s); call it off the main thread. */
    fun setPin(pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        prefs
            .edit()
            .putString(KEY_SALT, salt.encode())
            .putString(KEY_HASH, hash(pin, salt).encode())
            .putInt(KEY_WRONG, 0)
            .putLong(KEY_UNTIL, 0L)
            .apply()
    }

    /** Slow on purpose; call it off the main thread. */
    fun check(
        pin: String,
        now: Long = System.currentTimeMillis(),
    ): PinCheck {
        val attempts = PinAttempts(prefs.getInt(KEY_WRONG, 0), prefs.getLong(KEY_UNTIL, 0L))
        val salt = prefs.getString(KEY_SALT, null)?.decode()
        val stored = prefs.getString(KEY_HASH, null)?.decode()
        val result =
            when {
                attempts.lockedAt(now) -> PinCheck.LockedOut(attempts.lockedUntil)
                salt == null || stored == null -> PinCheck.Wrong(PinAttempts.MAX_WRONG)
                MessageDigest.isEqual(stored, hash(pin, salt)) -> {
                    attempts.onRight()
                    PinCheck.Ok
                }
                else -> attempts.onWrong(now)
            }
        prefs
            .edit()
            .putInt(KEY_WRONG, attempts.wrong)
            .putLong(KEY_UNTIL, attempts.lockedUntil)
            .apply()
        return result
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun hash(
        pin: String,
        salt: ByteArray,
    ): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.encode() = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decode() = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val NAME = "app_pin"
        const val KEY_HASH = "hash"
        const val KEY_SALT = "salt"
        const val KEY_WRONG = "wrong"
        const val KEY_UNTIL = "until"
        const val KEY_FINGER = "finger"
        const val SALT_BYTES = 16
        const val ITERATIONS = 20_000
        const val KEY_BITS = 256
    }
}
