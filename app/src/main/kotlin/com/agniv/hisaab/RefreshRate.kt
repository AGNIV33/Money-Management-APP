package com.agniv.hisaab

import android.app.Activity
import android.os.Build
import android.view.Display

/**
 * Asks for the phone's fastest screen mode at the current resolution (90, 120 or 144 Hz), so scrolling and
 * animations look as smooth as the screen allows. Android may still lower it (battery saver, heat, the user's
 * "smooth display" setting); on 60 Hz phones nothing changes. Only a hint, so it never costs a cheap phone anything.
 */
object RefreshRate {
    fun requestHighest(activity: Activity) {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display else legacyDisplay(activity)
        val best = display?.let { fastestMode(it.mode, it.supportedModes.toList()) } ?: return
        val params = activity.window.attributes
        params.preferredDisplayModeId = best.modeId
        activity.window.attributes = params
    }

    // Android 8–10 have no Activity.display.
    @Suppress("DEPRECATION")
    private fun legacyDisplay(activity: Activity): Display? = activity.windowManager.defaultDisplay

    /** The fastest mode with the same resolution as [current]; null when there is nothing faster. */
    internal fun fastestMode(
        current: Display.Mode,
        modes: List<Display.Mode>,
    ): Display.Mode? =
        modes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate }
            ?.takeIf { it.refreshRate > current.refreshRate }
}
