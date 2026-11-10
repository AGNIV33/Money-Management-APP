package com.agniv.hisaab.core.data.recurring

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.GenerateRecurring
import com.agniv.hisaab.core.domain.util.TimeProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes the repeating expenses that came due (ARCHITECTURE.md section 7). Reads and writes Room only,
 * so it works offline and with the app closed; the sync worker uploads the new expenses later.
 */
@HiltWorker
class RecurringWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val sessions: SessionRepository,
        private val generate: GenerateRecurring,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            sessions.session.first()?.let { generate(it) }
            return Result.success()
        }
    }

@Singleton
class RecurringScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val time: TimeProvider,
    ) {
        /** Once now (catches days the phone was off), then every day just after midnight. */
        fun start() {
            val work = WorkManager.getInstance(context)
            work.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RecurringWorker>().build())
            val now = LocalDateTime.ofInstant(time.now(), ZoneId.systemDefault())
            var next = now.toLocalDate().atTime(RUN_AT)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val daily =
                PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                    .build()
            work.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, daily)
        }

        private companion object {
            const val NOW = "recurring-now"
            const val DAILY = "recurring-daily"
            val RUN_AT: LocalTime = LocalTime.of(0, 30)
        }
    }
