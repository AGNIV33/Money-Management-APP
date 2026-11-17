package com.agniv.hisaab.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Which loans have their EMI reminders switched off. Kept only on this phone: reminders only ever go to the
 * loan's owner, so a family-wide setting is not needed.
 */
interface LoanReminderSettings {
    /** Ids of loans whose reminders are off. Every other loan reminds as usual. */
    val mutedLoanIds: Flow<Set<String>>

    suspend fun setReminderOn(
        loanId: String,
        on: Boolean,
    )
}
