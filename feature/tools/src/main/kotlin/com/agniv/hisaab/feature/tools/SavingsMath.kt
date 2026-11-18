package com.agniv.hisaab.feature.tools

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.loan.LoanTerms
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

internal data class SavingsResult(
    val invested: Money,
    val maturity: Money,
) {
    val earned get() = maturity - invested
}

internal data class AffordCheck(
    val result: Affordability,
    val maxLoan: Money?,
)

/** Interest / Savings tool maths. BigDecimal, not Double, per the money rule. */
internal object SavingsMath {
    private const val MONTHS_PER_YEAR = 12
    private const val QUARTERS_PER_YEAR = 4
    private const val MONTHS_PER_QUARTER = 3
    private const val CALC_DIGITS = 34
    private val CALC = MathContext(CALC_DIGITS)
    private const val BPS_PER_UNIT = 10_000
    private val BPS = BigDecimal(BPS_PER_UNIT)

    /** FD ([monthly] false) or monthly saving; null while incomplete or too large to count. */
    fun savings(
        amount: Money?,
        annualRateBps: Int?,
        months: Int?,
        monthly: Boolean,
    ): SavingsResult? {
        if (amount == null || annualRateBps == null || months == null) return null
        val invested = if (monthly) amount * months else amount
        val maturity =
            when {
                amount.paise > LoanTerms.MAX_PRINCIPAL_PAISE -> null
                monthly -> monthlyMaturity(amount, annualRateBps, months)
                else -> lumpSumMaturity(amount, annualRateBps, months)
            }
        return maturity?.let { SavingsResult(invested, it) }
    }

    /**
     * Fixed deposit the way Indian banks count it: interest is added every full quarter, and leftover months earn
     * simple interest. An estimate (no tax, no penalty). BigDecimal, not Double, per the money rule.
     */
    fun lumpSumMaturity(
        principal: Money,
        annualRateBps: Int,
        months: Int,
    ): Money? {
        val yearly = BigDecimal(annualRateBps).divide(BPS, CALC)
        val quarterly = yearly.divide(BigDecimal(QUARTERS_PER_YEAR), CALC)
        val afterQuarters = BigDecimal(principal.paise).multiply(BigDecimal.ONE.add(quarterly).pow(months / MONTHS_PER_QUARTER, CALC))
        val leftover = months % MONTHS_PER_QUARTER
        val simple = yearly.multiply(BigDecimal(leftover)).divide(BigDecimal(MONTHS_PER_YEAR), CALC)
        return toMoney(afterQuarters.multiply(BigDecimal.ONE.add(simple)))
    }

    /** Monthly saving (SIP style): [monthly] goes in at the start of each month and grows every month. */
    fun monthlyMaturity(
        monthly: Money,
        annualRateBps: Int,
        months: Int,
    ): Money? {
        if (annualRateBps == 0) return runCatching { monthly * months }.getOrNull()
        val i = BigDecimal(annualRateBps).divide(BPS.multiply(BigDecimal(MONTHS_PER_YEAR)), CALC)
        val growth = BigDecimal.ONE.add(i)
        val factor =
            growth
                .pow(months, CALC)
                .subtract(BigDecimal.ONE)
                .divide(i, CALC)
                .multiply(growth)
        return toMoney(BigDecimal(monthly.paise).multiply(factor))
    }

    /** Null when the answer does not fit in a Long of paise (huge amount, rate and years together). */
    private fun toMoney(paise: BigDecimal): Money? =
        runCatching { Money(paise.setScale(0, RoundingMode.HALF_UP).longValueExact()) }.getOrNull()
}
