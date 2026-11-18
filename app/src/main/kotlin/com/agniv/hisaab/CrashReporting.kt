package com.agniv.hisaab

import android.content.Context
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroid

/**
 * Crash reports (plan 5.5) through Sentry's free tier. Only when the app crashes or freezes (ANR), and only:
 * phone model, Android version, app version and the code location. Never names, emails, amounts, notes,
 * screenshots, the screen layout, taps, network calls or log lines. Off when the user turns it off in
 * Settings, in debug builds, and when no DSN is configured.
 */
object CrashReporting {
    fun start(
        context: Context,
        allowed: Boolean,
    ) {
        if (!allowed || BuildConfig.DEBUG || BuildConfig.SENTRY_DSN.isBlank()) return
        SentryAndroid.init(context) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.environment = BuildConfig.BUILD_TYPE
            options.isSendDefaultPii = false
            // No breadcrumbs: they record screens, taps, network calls and log lines, which can hold personal data.
            options.maxBreadcrumbs = 0
            options.isEnableUserInteractionBreadcrumbs = false
            options.isEnableActivityLifecycleBreadcrumbs = false
            options.isEnableAppComponentBreadcrumbs = false
            options.isEnableSystemEventBreadcrumbs = false
            options.isEnableNetworkEventBreadcrumbs = false
            options.isEnableAppLifecycleBreadcrumbs = false
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            // Crashes only: no performance tracing and no session pings.
            options.tracesSampleRate = null
            options.isEnableAutoSessionTracking = false
            options.setBeforeSend { event, _ -> clean(event) }
        }
    }

    /** User switched reports off in Settings: stop now, not only after a restart. */
    fun stop() {
        if (Sentry.isEnabled()) Sentry.close()
    }

    private fun clean(event: SentryEvent): SentryEvent {
        event.user = null
        event.serverName = null
        event.request = null
        event.breadcrumbs = null
        event.extras = null
        event.message?.let { message ->
            message.formatted = CrashScrubber.scrub(message.formatted)
            message.message = CrashScrubber.scrub(message.message)
            message.params = null
        }
        event.exceptions?.forEach { it.value = CrashScrubber.scrub(it.value) }
        return event
    }
}
