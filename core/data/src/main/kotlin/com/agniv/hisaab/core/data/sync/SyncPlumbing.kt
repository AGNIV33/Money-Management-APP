package com.agniv.hisaab.core.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncStore: DataStore<Preferences> by preferencesDataStore(name = "sync")

class DataStoreSyncState
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SyncStateStore {
        override suspend fun lastPulled(familyId: String): String? = context.syncStore.data.first()[key(familyId)]

        override suspend fun setLastPulled(
            familyId: String,
            serverTime: String,
        ) {
            context.syncStore.edit { it[key(familyId)] = serverTime }
        }

        private fun key(familyId: String) = stringPreferencesKey("last_pulled_$familyId")
    }

/** Lets repositories ask for a sync without knowing about WorkManager (and keeps them testable). */
fun interface SyncRequester {
    fun requestSync()
}

@Singleton
class SyncScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SyncRequester {
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Debounced: each new write restarts the 5 s timer, so a burst of edits makes one upload. */
        override fun requestSync() {
            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(online)
                    .setInitialDelay(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_OFF, ExistingWorkPolicy.REPLACE, request)
        }

        /** On app start: sync now-ish, and every 6 h in the background (ARCHITECTURE.md §5). */
        fun start() {
            requestSync()
            val periodic =
                PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                    .setConstraints(online)
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
        }

        private companion object {
            const val ONE_OFF = "sync-now"
            const val PERIODIC = "sync-periodic"
            const val DEBOUNCE_SECONDS = 5L
            const val BACKOFF_SECONDS = 30L
            const val PERIOD_HOURS = 6L
        }
    }

@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val engine: SyncEngine,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            engine.sync().fold(
                onSuccess = { Result.success() },
                onFailure = { if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure() },
            )

        private companion object {
            const val MAX_RETRIES = 5
        }
    }
