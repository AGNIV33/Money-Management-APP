package com.agniv.hisaab

import android.content.pm.ApplicationInfo
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
import com.agniv.hisaab.lock.PinStore

/**
 * Optional app lock (PRD F12). With a Kinly PIN set (boards' App Lock): the 4-digit PIN, plus the fingerprint when
 * the user allows it. Without one (locks turned on before the PIN existed): the phone's own fingerprint / face /
 * PIN prompt, as before. Kinly never sees the phone's credentials: Android's prompt answers yes or no.
 * Locked at app start and when you come back after [LOCK_AFTER_MS] away; the recent-apps picture is
 * blanked while the lock is on, so amounts are not shown in the app switcher.
 */
@Suppress("TooManyFunctions") // Small, single-purpose steps of one lock: start/stop, PIN, fingerprint, on/off.
class AppLock(
    private val activity: FragmentActivity,
    private val settings: AppSettings,
) {
    val pins = PinStore(activity)

    /** Read by the UI: true = show the lock screen instead of the app. */
    var locked by mutableStateOf(settings.appLock && canLock())
        private set

    /** Kinly PIN set: the lock screen shows the keypad instead of Android's prompt. */
    val usesPin: Boolean get() = pins.hasPin

    private fun canLock() = pins.hasPin || available()

    /** A fingerprint (or face) is enrolled, so the keypad can offer it. */
    fun fingerprintAvailable(): Boolean =
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    /** The right PIN was entered. */
    fun unlock() {
        locked = false
    }

    /** A new PIN was saved in the setup flow: the lock is on from now. */
    fun enableWithPin() {
        settings.appLock = true
        applySecureFlag()
    }

    /** App Lock switched off (or reset through Forgot PIN): PIN removed, nothing locked. */
    fun disable() {
        settings.appLock = false
        pins.clear()
        locked = false
        applySecureFlag()
    }

    private var leftAt = 0L
    private var prompting = false

    /** False when the phone has no screen lock or biometrics set up (then locking would lock the user out). */
    fun available(): Boolean = BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun onStart() {
        applySecureFlag()
        val away = SystemClock.elapsedRealtime() - leftAt
        val cameBackLate = leftAt != 0L && away >= LOCK_AFTER_MS
        if (cameBackLate && settings.appLock && canLock()) locked = true
    }

    fun onStop() {
        leftAt = SystemClock.elapsedRealtime()
    }

    /**
     * Shows Android's prompt. [onSuccess] runs only when the user really passed it. In PIN mode only the fingerprint
     * is offered (with a "Use PIN" button), so the phone's own PIN does not bypass the Kinly PIN.
     */
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
        val info =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(activity.getString(R.string.lock_prompt_title))
                .setSubtitle(activity.getString(R.string.lock_finger_body))
        if (pins.hasPin) {
            info.setAllowedAuthenticators(BIOMETRIC_WEAK).setNegativeButtonText(activity.getString(R.string.lock_use_pin))
        } else {
            info.setSubtitle(activity.getString(R.string.lock_prompt_subtitle)).setAllowedAuthenticators(AUTHENTICATORS)
        }
        prompt.authenticate(info.build())
    }

    private fun applySecureFlag() {
        val debuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (blockScreenshots(settings.appLock, debuggable)) {
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

/**
 * Screenshots (and the recent-apps picture) are blocked only when the app lock is on in a release build.
 * Debug builds always allow them, so screens can be captured while the app is being built and tested.
 */
internal fun blockScreenshots(
    appLockOn: Boolean,
    debugBuild: Boolean,
): Boolean = appLockOn && !debugBuild
