package com.agniv.hisaab.core.data.loan

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.agniv.hisaab.core.domain.loan.EmiReminder
import com.agniv.hisaab.core.domain.loan.EmiReminders
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.ObserveLoans
import com.agniv.hisaab.core.domain.util.TimeProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Shows the reminder. Implemented in :app, which owns channels, permission and the "Mark paid" action. */
interface EmiReminderNotifier {
    fun notify(reminder: EmiReminder)
}

private val Context.reminderStore: DataStore<Preferences> by preferencesDataStore(name = "emi_reminders")
private val SHOWN = stringSetPreferencesKey("shown")

/**
 * Daily EMI check (ARCHITECTURE.md §7). Reads Room only, so it works offline and with the app closed.
 * Every reminder shown is remembered, so running twice a day (app start + daily job) never repeats one.
 */
@HiltWorker
class EmiReminderWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val sessions: SessionRepository,
        private val observeLoans: ObserveLoans,
        private val notifier: EmiReminderNotifier,
        private val time: TimeProvider,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val session = sessions.session.first() ?: return Result.success()
            val today = time.today()
            val due = EmiReminders.due(observeLoans(session).first().items, session.userId)
            val store = applicationContext.reminderStore
            val shown = store.data.first()[SHOWN].orEmpty()
            val fresh = due.filter { it.key !in shown }
            fresh.forEach(notifier::notify)
            // Forget keys older than last month so the set stays tiny.
            val oldest = YearMonth.from(today).minusMonths(1).toString()
            store.edit { prefs ->
                prefs[SHOWN] = (shown + fresh.map { it.key }).filter { it.split(':').getOrNull(1).orEmpty() >= oldest }.toSet()
            }
            return Result.success()
        }
    }

@Singleton
class EmiReminderScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val time: TimeProvider,
    ) {
        /** Once now (catches a day the phone was off), then every day around 9 am. */
        fun start() {
            val work = WorkManager.getInstance(context)
            work.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<EmiReminderWorker>().build())
            val now = LocalDateTime.ofInstant(time.now(), java.time.ZoneId.systemDefault())
            var next = now.toLocalDate().atTime(REMIND_AT)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val daily =
                PeriodicWorkRequestBuilder<EmiReminderWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                    .build()
            work.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, daily)
        }

        private companion object {
            const val NOW = "emi-reminders-now"
            const val DAILY = "emi-reminders-daily"
            val REMIND_AT: LocalTime = LocalTime.of(9, 0)
        }
    }
