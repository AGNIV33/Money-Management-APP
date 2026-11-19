package com.agniv.hisaab.feature.tools

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.domain.loan.EmiMath
import com.agniv.hisaab.core.domain.loan.Installment
import com.agniv.hisaab.core.domain.loan.LoanForm
import com.agniv.hisaab.core.domain.loan.LoanTerms
import com.agniv.hisaab.core.domain.loan.PrepayMode
import com.agniv.hisaab.core.domain.loan.PrepaymentResult
import java.math.BigDecimal
import java.math.RoundingMode

/** One year of the repayment table (DESIGN.md §7.9: "yearly collapse"), so 30 years is 30 rows, not 360. */
internal data class YearRow(
    val year: Int,
    val principal: Money,
    val interest: Money,
    val balance: Money,
)

internal data class PrepayTotals(
    val interestBefore: Money,
    val interestAfter: Money,
    val monthsBefore: Int,
    val monthsAfter: Int,
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

    /** Prepayment result, or null while the inputs are incomplete or the lump sum is not less than what is left. */
    fun prepayOrNull(
        terms: LoanTerms?,
        paidCount: Int?,
        extra: Money?,
        mode: PrepayMode,
    ): PrepaymentResult? {
        if (terms == null || extra == null || paidCount == null) return null
        val valid = paidCount in 0 until terms.tenureMonths && extra < EmiMath.balanceAfter(terms, paidCount)
        return if (valid) EmiMath.prepay(terms, paidCount, extra, mode) else null
    }

    /** Burden for a new loan plus the biggest loan that keeps EMIs under 40%; null while inputs are incomplete. */
    fun afford(
        income: Money?,
        existingEmis: Money?,
        terms: LoanTerms?,
    ): AffordCheck? {
        if (income == null || existingEmis == null || terms == null) return null
        val result = EmiMath.affordability(income, existingEmis, EmiMath.emi(terms))
        return AffordCheck(result, maxAffordableLoan(income, existingEmis, terms.annualRateBps, terms.tenureMonths))
    }

    /** "50.1": [part] as a share of [whole], one decimal. */
    fun share(
        part: Money,
        whole: Money,
    ): String {
        if (whole.paise <= 0) return "0.0"
        val pct = BigDecimal(part.paise).multiply(BigDecimal(PERCENT)).divide(BigDecimal(whole.paise), 1, RoundingMode.HALF_UP)
        return pct.toPlainString()
    }

    /** Whole-loan interest and length, before and after a prepayment, for the before/after bars. */
    fun prepayTotals(
        terms: LoanTerms,
        paidCount: Int,
        result: PrepaymentResult,
    ): PrepayTotals {
        val paidInterest =
            EmiMath
                .schedule(terms)
                .take(paidCount)
                .map { it.interest }
                .sum()
        return PrepayTotals(
            interestBefore = paidInterest + result.interestLeftBefore,
            interestAfter = paidInterest + result.interestLeftAfter,
            monthsBefore = paidCount + result.monthsLeftBefore,
            monthsAfter = paidCount + result.monthsLeftAfter,
        )
    }
}
