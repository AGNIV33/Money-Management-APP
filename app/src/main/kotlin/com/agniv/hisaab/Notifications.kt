package com.agniv.hisaab

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

/** Each kind of notification Kinly sends. Settings has one switch per kind; all are on by default. */
enum class NotifyType(
    val key: String,
    @StringRes val label: Int,
    @StringRes val hint: Int,
) {
    BUDGET("budget", R.string.budget_channel, R.string.notify_budget_hint),
    SMART("smart", R.string.smart_channel, R.string.notify_smart_hint),
    REPORT("report", R.string.report_channel, R.string.notify_report_hint),
    EMI("emi", R.string.emi_channel, R.string.notify_emi_hint),
    UDHAAR("udhaar", R.string.udhaar_channel, R.string.notify_udhaar_hint),
    GOALS("goals", R.string.goal_channel, R.string.notify_goals_hint),
}

object Notifications {
    /** Android 13+ needs the user's OK; older phones always allow. */
    fun permitted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun allowed(
        context: Context,
        type: NotifyType,
    ): Boolean = permitted(context) && AppSettings(context).notifyOn(type)
}
