package com.agniv.hisaab.feature.tools

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.domain.loan.EmiMath
import com.agniv.hisaab.core.domain.loan.Installment
import com.agniv.hisaab.core.domain.loan.LoanForm
import com.agniv.hisaab.core.domain.loan.LoanTerms

/** One year of the repayment table (DESIGN.md §7.9: "yearly collapse"), so 30 years is 30 rows, not 360. */
internal data class YearRow(
    val year: Int,
    val principal: Money,
    val interest: Money,
    val balance: Money,
)

internal object ToolsLogic {
    private const val MONTHS_PER_YEAR = 12

    /** Shared by the three loan tools: reads amount, rate and tenure text; null while incomplete or impossible. */
    fun terms(
        amount: String,
        rate: String,
        tenure: String,
        inYears: Boolean,
    ): LoanTerms? {
        val principal = LoanForm.parseAmount(amount)?.takeIf { it.paise <= LoanTerms.MAX_PRINCIPAL_PAISE }
        val bps = LoanForm.parseRateBps(rate)
        val months = LoanForm.parseTenureMonths(tenure, inYears)
        val terms = if (principal != null && bps != null && months != null) LoanTerms(principal, bps, months) else null
        return terms?.takeIf(EmiMath::isRepayable)
    }

    fun yearly(schedule: List<Installment>): List<YearRow> =
        schedule
            .groupBy { (it.number - 1) / MONTHS_PER_YEAR + 1 }
            .map { (year, rows) ->
                YearRow(year, rows.map { it.principal }.sum(), rows.map { it.interest }.sum(), rows.last().balance)
            }

    /**
     * Largest loan whose EMI keeps total EMIs at or under [limitPercent] of income.
     * Null when existing EMIs already use up the limit.
     */
    fun maxAffordableLoan(
        income: Money,
        existingEmis: Money,
        annualRateBps: Int,
        months: Int,
        limitPercent: Long = EmiMath.DEFAULT_WARN_PERCENT.toLong(),
    ): Money? {
        val room = Money(income.paise * limitPercent / PERCENT) - existingEmis
        if (room.paise <= 0) return null
        return EmiMath.maxPrincipal(room, annualRateBps, months)
    }

    private const val PERCENT = 100L
}
