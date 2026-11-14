package com.agniv.hisaab

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.agniv.hisaab.core.data.budget.BudgetAlertMonitor
import com.agniv.hisaab.core.data.loan.EmiReminderScheduler
import com.agniv.hisaab.core.data.recurring.RecurringScheduler
import com.agniv.hisaab.core.data.sync.SyncScheduler
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HisaabApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncScheduler: Lazy<SyncScheduler>

    @Inject lateinit var budgetAlerts: Lazy<BudgetAlertMonitor>

    @Inject lateinit var emiReminders: Lazy<EmiReminderScheduler>

    @Inject lateinit var recurring: Lazy<RecurringScheduler>

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Faster cold start (5.1): the schedulers pull in Room, WorkManager and Supabase, so they are built and
        // started off the main thread after the first frame can already be drawn. Lazy keeps injection itself cheap.
        appScope.launch {
            // Upload anything saved offline and pull family changes; WorkManager waits for internet.
            syncScheduler.get().start()
            // Budget alerts at 80% / 100%, computed from the phone's own data, so they work offline.
            budgetAlerts.get().start(appScope)
            // EMI reminders 3 days / 1 day before and on the due day; also works when the app is closed.
            emiReminders.get().start()
            // Rent, maid, milk and other repeating expenses are made on their day, even with the app closed.
            recurring.get().start()
        }
    }
}
