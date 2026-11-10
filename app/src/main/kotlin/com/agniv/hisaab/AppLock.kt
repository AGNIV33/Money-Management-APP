package com.agniv.hisaab

import android.os.SystemClock
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Optional app lock (PRD F12): fingerprint / face, or the phone's own PIN, pattern or password.
 * Kinly never sees or stores any of it: Android's prompt answers yes or no.
 * Locked at app start and when you come back after [LOCK_AFTER_MS] away; the recent-apps picture is
 * blanked while the lock is on, so amounts are not shown in the app switcher.
 */
class AppLock(
    private val activity: FragmentActivity,
    private val settings: AppSettings,
) {
    /** Read by the UI: true = show the lock screen instead of the app. */
    var locked by mutableStateOf(settings.appLock && available())
        private set

    private var leftAt = 0L
    private var prompting = false

    /** False when the phone has no screen lock or biometrics set up (then locking would lock the user out). */
    fun available(): Boolean = BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun onStart() {
        applySecureFlag()
        val away = SystemClock.elapsedRealtime() - leftAt
        val cameBackLate = leftAt != 0L && away >= LOCK_AFTER_MS
        if (cameBackLate && settings.appLock && available()) locked = true
    }

    fun onStop() {
        leftAt = SystemClock.elapsedRealtime()
    }

    /** Shows Android's prompt. [onSuccess] runs only when the user really passed it. */
    fun authenticate(onSuccess: () -> Unit = {}) {
        if (prompting) return
        prompting = true
        val prompt =
            BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        prompting = false
                        locked = false
                        onSuccess()
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        // Cancelled or locked out: stay locked; the screen has an Unlock button to try again.
                        prompting = false
                    }
                },
            )
        prompt.authenticate(
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(activity.getString(R.string.lock_prompt_title))
                .setSubtitle(activity.getString(R.string.lock_prompt_subtitle))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }

    /** Turn the lock on or off. Turning on asks once, so the user knows it works before relying on it. */
    fun setEnabled(
        on: Boolean,
        onDone: (Boolean) -> Unit,
    ) {
        if (!on) {
            settings.appLock = false
            applySecureFlag()
            onDone(false)
            return
        }
        if (!available()) {
            onDone(false)
            return
        }
        authenticate {
            settings.appLock = true
            applySecureFlag()
            onDone(true)
        }
    }

    private fun applySecureFlag() {
        if (settings.appLock) {
            activity.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private companion object {
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        const val LOCK_AFTER_MS = 30_000L
    }
}
