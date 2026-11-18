package com.agniv.hisaab

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.agniv.hisaab.alerts.AlertKind
import com.agniv.hisaab.alerts.AlertLog
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.repository.CategoryRepository
import com.agniv.hisaab.core.domain.repository.ExpenseRepository
import com.agniv.hisaab.core.domain.repository.FamilyRepository
import com.agniv.hisaab.core.domain.repository.RecurringRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.ObserveLoans
import com.agniv.hisaab.core.domain.util.TimeProvider
import com.agniv.hisaab.core.ml.insight.InsightEngine
import com.agniv.hisaab.core.ml.insight.InsightInput
import com.agniv.hisaab.core.ml.insight.SmartAlert
import com.agniv.hisaab.core.ml.insight.SmartAlerts
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Once a day in the evening: runs the same on-device insight engine as the Insights sheet and turns the
 * "needs a look" ones into notifications (see SmartAlerts). Reads Room only, so it works offline and with the app closed.
 * Each alert is remembered once shown, so it never repeats.
 */
@HiltWorker
class SmartAlertWorker
    // One evening job needs everything the Insights sheet reads.
    @Suppress("LongParameterList")
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val sessions: SessionRepository,
        private val expenses: ExpenseRepository,
        private val categories: CategoryRepository,
        private val families: FamilyRepository,
        private val recurring: RecurringRepository,
        private val observeLoans: ObserveLoans,
        private val notifier: SmartAlertNotifier,
        private val time: TimeProvider,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            // Runs even when notifications are blocked: the in-app Alerts list still gets what it finds.
            val session = sessions.session.first() ?: return Result.success()
            val today = time.today()
            val month = YearMonth.from(today)
            // Same window as the Insights sheet, so the notification and the sheet agree.
            val list =
                expenses.observeExpenses(session.familyId, session.userId, month.minusMonths(HISTORY_MONTHS).atDay(1), today).first()
            val insights =
                InsightEngine.compute(
                    InsightInput(
                        today,
                        list,
                        families.observeFamily(session.familyId).first()?.monthlyBudget,
                        observeLoans(session).first().monthlyEmi,
                        recurring.observe(session.familyId, session.userId).first(),
                    ),
                )
            val lastMonth = list.filter { !it.deleted && YearMonth.from(it.date) == month.minusMonths(1) }.map { it.amount }.sum()
            val alerts = SmartAlerts.pick(insights, today, lastMonth)
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val shown = prefs.getStringSet(SHOWN, emptySet()).orEmpty()
            val fresh = alerts.filter { it.key !in shown }
            if (fresh.isNotEmpty()) {
                val names = categories.observeCategories(session.familyId).first().associateBy { it.id }
                fresh.forEach { notifier.notify(it, names) }
            }
            // Keep only this month and last month, so the set stays tiny.
            val oldest = month.minusMonths(1).toString()
            val keep = (shown + fresh.map { it.key }).filter { it.split(':').getOrNull(1).orEmpty() >= oldest }.toSet()
            prefs.edit().putStringSet(SHOWN, keep).apply()
            return Result.success()
        }

        private companion object {
            const val HISTORY_MONTHS = 5L
            const val PREFS = "smart_alerts"
            const val SHOWN = "shown"
        }
    }

@Singleton
class SmartAlertScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val time: TimeProvider,
    ) {
        /** Every evening around 8 pm, after most of the day's spending is in. Not at app start: the user is looking already. */
        fun start() {
            val now = LocalDateTime.ofInstant(time.now(), ZoneId.systemDefault())
            var next = now.toLocalDate().atTime(RUN_AT)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val daily =
                PeriodicWorkRequestBuilder<SmartAlertWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, daily)
        }

        private companion object {
            const val DAILY = "smart-alerts-daily"
            val RUN_AT: LocalTime = LocalTime.of(20, 0)
        }
    }

/** Words and shows a smart alert in the app's language. Tapping it opens the Insights sheet. */
@Singleton
class SmartAlertNotifier
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
    ) {
        fun notify(
            alert: SmartAlert,
            categories: Map<String, Category>,
        ) {
            val type = if (alert is SmartAlert.ReportReady) NotifyType.REPORT else NotifyType.SMART
            if (!AlertLog.wanted(appContext, type)) return
            val settings = AppSettings(appContext)
            val context = settings.wrap(appContext)
            val hindi = (settings.language ?: Locale.getDefault().language) == "hi"

            fun name(id: String): String {
                val c = categories[id] ?: return context.getString(R.string.smart_this_category)
                return if (hindi && !c.nameHi.isNullOrBlank()) c.nameHi.orEmpty() else c.name
            }
            val (title, text) =
                when (alert) {
                    is SmartAlert.CategoryJump ->
                        context.getString(R.string.smart_jump_title, name(alert.categoryId)) to
                            context.getString(R.string.smart_jump_text, alert.percent, alert.spent.formatted())
                    is SmartAlert.BigExpense ->
                        context.getString(R.string.smart_big_title) to
                            context.getString(
                                R.string.smart_big_text,
                                name(alert.categoryId),
                                alert.amount.formatted(),
                                alert.typical.formatted(),
                            )
                    is SmartAlert.BudgetRunsOut ->
                        context.getString(R.string.smart_pace_title) to
                            context.getString(R.string.smart_pace_text, alert.day, alert.safePerDay.formatted())
                    is SmartAlert.ReportReady -> {
                        val locale = if (hindi) Locale.forLanguageTag("hi") else Locale.ENGLISH
                        context.getString(R.string.report_ready_title, alert.month.month.getDisplayName(TextStyle.FULL, locale)) to
                            context.getString(R.string.report_ready_text)
                    }
                }
            ensureChannel(context, type)
            val id = ID_BASE + (alert.key.hashCode() and ID_MASK)
            val open =
                PendingIntent.getActivity(
                    appContext,
                    id,
                    Intent(appContext, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainActivity.EXTRA_OPEN_INSIGHTS, true),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat
                    .Builder(appContext, channelOf(type))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
            AlertLog.deliver(appContext, alert.kind(), id, notification)
        }

        private fun channelOf(type: NotifyType) = if (type == NotifyType.REPORT) CHANNEL_REPORT else CHANNEL_SMART

        private fun ensureChannel(
            context: Context,
            type: NotifyType,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            // Report: a calm "have a look"; alerts: normal, they point at something unusual.
            val importance = if (type == NotifyType.REPORT) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelOf(type), context.getString(type.label), importance)
            appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private companion object {
            const val CHANNEL_SMART = "smart_alerts"
            const val CHANNEL_REPORT = "monthly_report"

            // Clear of budget (1001/1002), EMI (2000+), udhaar (300000+) and goal (400000+) ids.
            const val ID_BASE = 500_000
            const val ID_MASK = 0xFFFF
        }
    }

private fun SmartAlert.kind(): AlertKind =
    when (this) {
        is SmartAlert.CategoryJump -> AlertKind.CATEGORY_JUMP
        is SmartAlert.BigExpense -> AlertKind.UNUSUAL
        is SmartAlert.BudgetRunsOut -> AlertKind.BUDGET_PACE
        is SmartAlert.ReportReady -> AlertKind.REPORT
    }
