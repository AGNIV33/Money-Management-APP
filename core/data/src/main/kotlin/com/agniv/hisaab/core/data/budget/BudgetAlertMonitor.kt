package com.agniv.hisaab.core.data.budget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.domain.repository.FamilyRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.BudgetLevel
import com.agniv.hisaab.core.domain.usecase.ObserveMonthExpenses
import com.agniv.hisaab.core.domain.usecase.budgetLevel
import com.agniv.hisaab.core.domain.util.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** Shows the actual notification. Implemented in :app, which owns channels and permissions. */
interface BudgetAlertNotifier {
    fun notify(
        level: BudgetLevel,
        spent: Money,
        budget: Money,
    )
}

private val Context.alertStore: DataStore<Preferences> by preferencesDataStore(name = "budget_alerts")

/**
 * Watches this month's spending against the family budget and notifies once at 80% and once at 100%.
 * Works offline: it reads Room, not the network. The last level shown is remembered per family and month,
 * so reopening the app doesn't repeat an alert. If spending drops back (an expense is deleted), the
 * memory drops too, so crossing the line again alerts again.
 */
@Singleton
class BudgetAlertMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sessions: SessionRepository,
        private val families: FamilyRepository,
        private val monthExpenses: ObserveMonthExpenses,
        private val notifier: BudgetAlertNotifier,
        private val time: TimeProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        fun start(scope: CoroutineScope) {
            scope.launch {
                sessions.session
                    .filterNotNull()
                    .flatMapLatest { session ->
                        val month = YearMonth.from(time.today())
                        combine(families.observeFamily(session.familyId), monthExpenses(session, month)) { family, expenses ->
                            Triple(session.familyId to month, family?.monthlyBudget, expenses.map { it.amount }.sum())
                        }
                    }.distinctUntilChanged()
                    .collect { (key, budget, spent) -> check(key.first, key.second, budget, spent) }
            }
        }

        private suspend fun check(
            familyId: String,
            month: YearMonth,
            budget: Money?,
            spent: Money,
        ) {
            val key = intPreferencesKey("level_${familyId}_$month")
            val level = if (budget == null) BudgetLevel.OK else budgetLevel(spent, budget)
            val shown = context.alertStore.data.first()[key] ?: 0
            if (level.ordinal > shown && budget != null) notifier.notify(level, spent, budget)
            if (level.ordinal != shown) context.alertStore.edit { it[key] = level.ordinal }
        }
    }
