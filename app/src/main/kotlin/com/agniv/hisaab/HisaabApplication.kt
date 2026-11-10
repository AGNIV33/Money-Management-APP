package com.agniv.hisaab

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.agniv.hisaab.core.data.budget.BudgetAlertMonitor
import com.agniv.hisaab.core.data.loan.EmiReminderScheduler
import com.agniv.hisaab.core.data.recurring.RecurringScheduler
import com.agniv.hisaab.core.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class HisaabApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncScheduler: SyncScheduler

    @Inject lateinit var budgetAlerts: BudgetAlertMonitor

    @Inject lateinit var emiReminders: EmiReminderScheduler

    @Inject lateinit var recurring: RecurringScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Upload anything saved offline and pull family changes; WorkManager waits for internet.
        syncScheduler.start()
        // Budget alerts at 80% / 100%, computed from the phone's own data, so they work offline.
        budgetAlerts.start(appScope)
        // EMI reminders 3 days / 1 day before and on the due day; also works when the app is closed.
        emiReminders.start()
        // Rent, maid, milk and other repeating expenses are made on their day, even with the app closed.
        recurring.start()
    }
}
