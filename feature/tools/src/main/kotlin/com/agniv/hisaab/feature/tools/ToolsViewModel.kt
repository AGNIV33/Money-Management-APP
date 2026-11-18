package com.agniv.hisaab.feature.tools

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.loan.EmiMath
import com.agniv.hisaab.core.domain.loan.LoanTerms
import com.agniv.hisaab.core.domain.loan.PrepayMode
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.LoanItem
import com.agniv.hisaab.core.domain.usecase.ObserveLoans
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What prepaying [lump] on one of the family's own loans would save. Null when there is no loan to show it on. */
@Immutable
data class PrepayTip(
    val lump: Money,
    val interestSaved: Money,
    val monthsSaved: Int,
)

/** The calculators keep their own inputs; this offers the family's loans to fill them from, plus the hub's lists. */
@Immutable
data class ToolsUiState(
    val openLoans: List<LoanItem> = emptyList(),
    val monthlyEmi: Money = Money.ZERO,
    val recent: List<RecentResult> = emptyList(),
    val tip: PrepayTip? = null,
)

@HiltViewModel
class ToolsViewModel
    @Inject
    constructor(
        sessions: SessionRepository,
        observeLoans: ObserveLoans,
        private val recentStore: RecentResultsStore,
        dispatchers: DispatcherProvider,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        private val loans =
            sessions.session
                .filterNotNull()
                .flatMapLatest { observeLoans(it) }
                .map { overview -> overview.items.filter { !it.progress.isClosed } to overview.monthlyEmi }
                // Signed-out or still loading: the hub still shows recent results.
                .onStart { emit(emptyList<LoanItem>() to Money.ZERO) }

        val state: StateFlow<ToolsUiState> =
            combine(loans, recentStore.recent.onStart { emit(emptyList()) }) { (open, monthlyEmi), recent ->
                ToolsUiState(open, monthlyEmi, recent, prepayTip(open))
            }.flowOn(dispatchers.default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ToolsUiState())

        fun saveRecent(result: RecentResult) {
            viewModelScope.launch { recentStore.add(result) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

private const val TIP_LUMP_RUPEES = 50_000L
private const val TIP_MIN_BALANCE_SHARE = 2

/** Uses the first open loan where a ₹50,000 lump sum is less than half of what is left. Real numbers, not made up. */
internal fun prepayTip(open: List<LoanItem>): PrepayTip? {
    val lump = Money.ofRupees(TIP_LUMP_RUPEES)
    return open.firstNotNullOfOrNull { tipFor(it, lump) }
}

private fun tipFor(
    item: LoanItem,
    lump: Money,
): PrepayTip? {
    val loan = item.loan
    val terms =
        runCatching { LoanTerms(loan.principal, loan.annualRateBps, loan.tenureMonths) }
            .getOrNull()
            ?.takeIf(EmiMath::isRepayable)
    val paid = item.progress.paidCount
    val usable = terms != null && paid in 0 until terms.tenureMonths && EmiMath.balanceAfter(terms, paid) > lump * TIP_MIN_BALANCE_SHARE
    val result = if (usable && terms != null) EmiMath.prepay(terms, paid, lump, PrepayMode.REDUCE_TENURE) else null
    return result?.takeIf { it.interestSaved.paise > 0 }?.let { PrepayTip(lump, it.interestSaved, it.monthsSaved) }
}
