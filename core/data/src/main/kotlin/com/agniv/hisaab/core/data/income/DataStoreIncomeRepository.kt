package com.agniv.hisaab.core.data.income

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.repository.IncomeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Its own file, separate from anything that syncs, so it can never ride along with an upload by mistake.
private val Context.incomeStore: DataStore<Preferences> by preferencesDataStore(name = "private_income")
private val INCOME_PAISE = longPreferencesKey("monthly_income_paise")

@Singleton
class DataStoreIncomeRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : IncomeRepository {
        override val income: Flow<Money?> =
            context.incomeStore.data.map { prefs -> prefs[INCOME_PAISE]?.takeIf { it > 0 }?.let(::Money) }

        override suspend fun set(income: Money?) {
            context.incomeStore.edit { prefs ->
                if (income == null || income.paise <= 0) prefs.remove(INCOME_PAISE) else prefs[INCOME_PAISE] = income.paise
            }
        }
    }
