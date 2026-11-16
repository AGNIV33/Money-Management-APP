package com.agniv.hisaab.alerts

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.agniv.hisaab.AppSettings
import com.agniv.hisaab.Notifications
import com.agniv.hisaab.NotifyType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** What an alert was about; picks its icon, colour, filter chip and where tapping it leads. */
enum class AlertKind(
    val type: NotifyType,
) {
    UNUSUAL(NotifyType.SMART),
    CATEGORY_JUMP(NotifyType.SMART),
    BUDGET_PACE(NotifyType.SMART),
    BUDGET_WARNING(NotifyType.BUDGET),
    BUDGET_OVER(NotifyType.BUDGET),
    REPORT(NotifyType.REPORT),
    EMI(NotifyType.EMI),
    UDHAAR(NotifyType.UDHAAR),
    GOAL(NotifyType.GOALS),
}

data class AlertEntry(
    val id: Long,
    val kind: AlertKind,
    val title: String,
    val text: String,
    val at: Long,
)

/**
 * The in-app Alerts list (board "Alerts"). Every alert Kinly raises is kept here on this phone only: never synced,
 * never sent anywhere, at most [MAX_KEPT] and none older than [MAX_AGE_DAYS] days. An alert type switched off in
 * Notification settings is neither shown as a notification nor kept.
 */
object AlertLog {
    private const val PREFS = "kinly_alerts"
    private const val KEY = "entries"
    private const val MAX_KEPT = 50
    private const val MAX_AGE_DAYS = 60L
    private const val DAY_MS = 86_400_000L

    private val entries = MutableStateFlow<List<AlertEntry>?>(null)

    /** Newest first. */
    fun observe(context: Context): StateFlow<List<AlertEntry>?> {
        if (entries.value == null) entries.value = load(context)
        return entries.asStateFlow()
    }

    /** True when the user wants this kind of alert at all (the switch in Notification settings). */
    fun wanted(
        context: Context,
        type: NotifyType,
    ): Boolean = AppSettings(context).notifyOn(type)

    /**
     * Keeps the alert in the list, then shows it as a notification if Android allows it. The list still gets it
     * when notifications are blocked at phone level, so nothing is missed.
     */
    @SuppressLint("MissingPermission") // Checked by Notifications.permitted just before.
    fun deliver(
        context: Context,
        kind: AlertKind,
        notificationId: Int,
        notification: Notification,
    ) {
        val title =
            notification.extras
                .getCharSequence(Notification.EXTRA_TITLE)
                ?.toString()
                .orEmpty()
        val text =
            notification.extras
                .getCharSequence(Notification.EXTRA_TEXT)
                ?.toString()
                .orEmpty()
        record(context, kind, title, text)
        if (Notifications.permitted(context)) NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    @Synchronized
    fun record(
        context: Context,
        kind: AlertKind,
        title: String,
        text: String,
        now: Long = System.currentTimeMillis(),
    ) {
        val current = entries.value ?: load(context)
        // The same alert again (e.g. a re-run of the daily check) replaces the old one instead of piling up.
        val kept = current.filterNot { it.kind == kind && it.title == title && it.text == text }
        val next = (listOf(AlertEntry(now, kind, title, text, now)) + kept).prune(now)
        save(context, next)
    }

    @Synchronized
    fun dismiss(
        context: Context,
        id: Long,
    ) {
        save(context, (entries.value ?: load(context)).filterNot { it.id == id })
    }

    /** Undo after a swipe. */
    @Synchronized
    fun restore(
        context: Context,
        entry: AlertEntry,
    ) {
        save(context, ((entries.value ?: load(context)) + entry).sortedByDescending { it.at })
    }

    private fun List<AlertEntry>.prune(now: Long) = filter { now - it.at <= MAX_AGE_DAYS * DAY_MS }.take(MAX_KEPT)

    private fun save(
        context: Context,
        list: List<AlertEntry>,
    ) {
        entries.value = list
        val json = JSONArray()
        list.forEach {
            json.put(
                JSONObject()
                    .put("id", it.id)
                    .put("kind", it.kind.name)
                    .put("title", it.title)
                    .put("text", it.text)
                    .put("at", it.at),
            )
        }
        prefs(context).edit().putString(KEY, json.toString()).apply()
    }

    private fun load(context: Context): List<AlertEntry> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { i ->
                val o = json.getJSONObject(i)
                val kind = runCatching { AlertKind.valueOf(o.getString("kind")) }.getOrNull() ?: return@mapNotNull null
                AlertEntry(o.getLong("id"), kind, o.getString("title"), o.getString("text"), o.getLong("at"))
            }
        }.getOrDefault(emptyList())
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
