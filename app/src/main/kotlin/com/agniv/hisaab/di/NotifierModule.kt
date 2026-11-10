package com.agniv.hisaab.di

import com.agniv.hisaab.AndroidBudgetNotifier
import com.agniv.hisaab.AndroidEmiNotifier
import com.agniv.hisaab.AndroidUdhaarNotifier
import com.agniv.hisaab.core.data.budget.BudgetAlertNotifier
import com.agniv.hisaab.core.data.loan.EmiReminderNotifier
import com.agniv.hisaab.core.data.loan.UdhaarReminderNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotifierModule {
    @Binds
    abstract fun bindBudgetNotifier(impl: AndroidBudgetNotifier): BudgetAlertNotifier

    @Binds
    abstract fun bindEmiNotifier(impl: AndroidEmiNotifier): EmiReminderNotifier

    @Binds
    abstract fun bindUdhaarNotifier(impl: AndroidUdhaarNotifier): UdhaarReminderNotifier
}
