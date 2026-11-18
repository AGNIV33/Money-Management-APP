package com.agniv.hisaab.core.domain.repository

import com.agniv.hisaab.core.common.money.Money
import kotlinx.coroutines.flow.Flow

/**
 * Optional monthly take-home income, off by default (null). Kept only on this phone: never synced to the
 * family, never sent to any server or AI. Used for the savings-rate insight and the EMI-to-income share.
 */
interface IncomeRepository {
    val income: Flow<Money?>

    /** Null forgets it. */
    suspend fun set(income: Money?)
}
