package com.agniv.hisaab.core.network

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RemoteModule {
    @Binds abstract fun expenseRemote(impl: SupabaseExpenseRemote): ExpenseRemote

    @Binds abstract fun loanRemote(impl: SupabaseLoanRemote): LoanRemote

    @Binds abstract fun recurringRemote(impl: SupabaseRecurringRemote): RecurringRemote
}
