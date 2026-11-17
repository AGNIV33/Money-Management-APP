package com.agniv.hisaab.core.data.loan

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.agniv.hisaab.core.domain.repository.LoanReminderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.loanReminderStore: DataStore<Preferences> by preferencesDataStore(name = "loan_reminder_settings")
private val MUTED = stringSetPreferencesKey("muted_loan_ids")

@Singleton
class DataStoreLoanReminderSettings
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : LoanReminderSettings {
        override val mutedLoanIds: Flow<Set<String>> = context.loanReminderStore.data.map { it[MUTED].orEmpty() }

        override suspend fun setReminderOn(
            loanId: String,
            on: Boolean,
        ) {
            context.loanReminderStore.edit { prefs ->
                val now = prefs[MUTED].orEmpty()
                prefs[MUTED] = if (on) now - loanId else now + loanId
            }
        }
    }
