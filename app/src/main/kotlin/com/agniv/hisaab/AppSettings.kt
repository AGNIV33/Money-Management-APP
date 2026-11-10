package com.agniv.hisaab

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Tiny device settings read before any screen exists (language, first-run flag).
 * SharedPreferences instead of DataStore on purpose: attachBaseContext needs the language synchronously.
 */
class AppSettings(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** "en", "hi", or null = follow the phone. */
    var language: String?
        get() = prefs.getString(KEY_LANGUAGE, null)
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /** Emoji picked as the profile picture; null = show the first letter of the name. Stays on this phone. */
    var avatar: String?
        get() = prefs.getString(KEY_AVATAR, null)
        set(value) = prefs.edit().putString(KEY_AVATAR, value).apply()

    /** True once the person has read and accepted what the voice assistant shares (asked before first use). */
    var assistantConsent: Boolean
        get() = prefs.getBoolean(KEY_ASSISTANT_CONSENT, false)
        set(value) = prefs.edit().putBoolean(KEY_ASSISTANT_CONSENT, value).apply()

    /** Optional app lock (fingerprint or phone PIN). */
    var appLock: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCK, value).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    /** Context whose resources use the chosen language; unchanged when none was chosen. */
    fun wrap(base: Context): Context {
        val code = language ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(code))
        return base.createConfigurationContext(config)
    }

    private companion object {
        const val NAME = "device_settings"
        const val KEY_LANGUAGE = "language"
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_APP_LOCK = "app_lock"
        const val KEY_AVATAR = "avatar"
        const val KEY_ASSISTANT_CONSENT = "assistant_consent"
    }
}
