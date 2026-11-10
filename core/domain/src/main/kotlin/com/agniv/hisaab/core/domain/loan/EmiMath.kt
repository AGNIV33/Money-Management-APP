package com.agniv.hisaab.core.domain.loan

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.sum
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * A loan as the bank states it. Rate is in basis points (8.5% = 850) so no floating point is ever
 * stored. Limits are generous but finite so arithmetic on paise can never overflow a Long.
 */
data class LoanTerms(
    val principal: Money,
    val annualRateBps: Int,
    val tenureMonths: Int,
) {
    init {
        require(principal.paise in 1..MAX_PRINCIPAL_PAISE) { "principal must be between ₹0.01 and ₹100 crore" }
        require(annualRateBps in 0..MAX_RATE_BPS) { "rate must be 0% to 42%" }
        require(tenureMonths in 1..MAX_TENURE_MONTHS) { "tenure must be 1 to 480 months" }
    }

    companion object {
        const val MAX_PRINCIPAL_PAISE = 100_000_000_000L
        const val MAX_RATE_BPS = 4_200
        const val MAX_TENURE_MONTHS = 480
    }
}

/** One row of the repayment table. [balance] is what is still owed after this payment. */
data class Installment(
    val number: Int,
    val payment: Money,
    val interest: Money,
    val principal: Money,
    val balance: Money,
)

data class LoanSummary(
    val emi: Money,
    val totalInterest: Money,
    val totalPayable: Money,
)

enum class PrepayMode {
    /** Keep the same EMI, finish the loan sooner. */
    REDUCE_TENURE,

    /** Keep the same end date, pay a smaller EMI. */
    REDUCE_EMI,
}

data class PrepaymentResult(
    val mode: PrepayMode,
    val balanceBefore: Money,
    val balanceAfter: Money,
    val emiBefore: Money,
    val emiAfter: Money,
    val monthsLeftBefore: Int,
    val monthsLeftAfter: Int,
    val interestLeftBefore: Money,
    val interestLeftAfter: Money,
) {
    val interestSaved: Money get() = interestLeftBefore - interestLeftAfter
    val monthsSaved: Int get() = monthsLeftBefore - monthsLeftAfter
}

enum class BurdenLevel { COMFORTABLE, CAUTION, RISKY }

data class Affordability(
    val newEmi: Money,
    val totalEmiAfter: Money,
    /** Share of income going to EMIs after the new loan, e.g. 42.5 for 42.5%. */
    val burdenPercent: Double,
    val level: BurdenLevel,
)

/**
 * All loan maths, in paise, per PRD §"Formulas":
 * EMI = P·r·(1+r)^n / ((1+r)^n − 1), r = annual rate / 12; r = 0 → P / n.
 * Interest each month is rounded half-up to the paisa and the last instalment absorbs the leftover,
 * exactly as banks' tables do, so the balance always ends at exactly ₹0.
 */
@Suppress("TooManyFunctions") // one cohesive calculator; splitting it would only scatter the formulas
object EmiMath {
    private const val BPS_DENOMINATOR = 10_000L
    private const val MONTHS_PER_YEAR = 12L
    private const val MONTHLY_DENOMINATOR = BPS_DENOMINATOR * MONTHS_PER_YEAR
    private const val SAFETY_FACTOR = 2
    private const val SAFETY_LIMIT = LoanTerms.MAX_TENURE_MONTHS * SAFETY_FACTOR
    private const val PERCENT = 100.0
    private const val PRECISION_DIGITS = 60
    private val PRECISION = MathContext(PRECISION_DIGITS)

    /** Default "too much debt" line: EMIs above 40% of income (PRD). */
    const val DEFAULT_WARN_PERCENT = 40.0
    private const val CAUTION_FROM_PERCENT = 30.0

    fun emi(terms: LoanTerms): Money = emi(terms.principal, terms.annualRateBps, terms.tenureMonths)

    fun emi(
        principal: Money,
        annualRateBps: Int,
        months: Int,
    ): Money {
        if (annualRateBps == 0) return Money(divideRound(principal.paise, months.toLong()))
        val r = monthlyRate(annualRateBps)
        val growth = BigDecimal.ONE.add(r).pow(months, PRECISION)
        val emi =
            BigDecimal(principal.paise)
                .multiply(r)
                .multiply(growth)
                .divide(growth.subtract(BigDecimal.ONE), PRECISION)
        return Money(emi.setScale(0, RoundingMode.HALF_UP).longValueExact())
    }

    /**
     * False when the rate and tenure are so extreme that the first month's principal is under one paisa
     * (e.g. 36% for 40 years), or the EMI rounds to nothing (₹1 over 40 years), so no whole-paise
     * table exists. Screens check this before showing a table.
     */
    fun isRepayable(terms: LoanTerms): Boolean =
        emi(terms).paise > divideRound(terms.principal.paise * terms.annualRateBps, MONTHLY_DENOMINATOR)

    fun schedule(terms: LoanTerms): List<Installment> {
        require(isRepayable(terms)) { "this rate and tenure cannot be repaid in whole paise" }
        return amortize(terms.principal, terms.annualRateBps, emi(terms), terms.tenureMonths)
    }

    fun summary(terms: LoanTerms): LoanSummary {
        val rows = schedule(terms)
        val interest = rows.map { it.interest }.sum()
        return LoanSummary(emi(terms), interest, terms.principal + interest)
    }

    /** What is still owed after [paidInstallments] payments (0 = nothing paid yet). */
    fun balanceAfter(
        terms: LoanTerms,
        paidInstallments: Int,
    ): Money {
        require(paidInstallments in 0..terms.tenureMonths) { "paid instalments out of range" }
        return if (paidInstallments == 0) terms.principal else schedule(terms)[paidInstallments - 1].balance
    }

    /**
     * Effect of paying [extra] in one go right after instalment number [paidInstallments].
     * [extra] must be more than zero and less than the balance (paying everything is just closing the loan).
     */
    fun prepay(
        terms: LoanTerms,
        paidInstallments: Int,
        extra: Money,
        mode: PrepayMode,
    ): PrepaymentResult {
        require(paidInstallments in 0 until terms.tenureMonths) { "no instalments left to prepay" }
        val emi = emi(terms)
        val rows = schedule(terms)
        val balance = if (paidInstallments == 0) terms.principal else rows[paidInstallments - 1].balance
        require(extra.paise > 0 && extra.paise < balance.paise) { "extra payment must be more than ₹0 and less than the balance" }
        val monthsLeft = terms.tenureMonths - paidInstallments
        val interestBefore = rows.drop(paidInstallments).map { it.interest }.sum()
        val newBalance = balance - extra
        val after =
            when (mode) {
                PrepayMode.REDUCE_TENURE -> amortize(newBalance, terms.annualRateBps, emi, null)
                PrepayMode.REDUCE_EMI -> {
                    val smaller = emi(newBalance, terms.annualRateBps, monthsLeft)
                    amortize(newBalance, terms.annualRateBps, smaller, monthsLeft)
                }
            }
        return PrepaymentResult(
            mode = mode,
            balanceBefore = balance,
            balanceAfter = newBalance,
            emiBefore = emi,
            emiAfter = if (mode == PrepayMode.REDUCE_TENURE) emi else after.first().payment,
            monthsLeftBefore = monthsLeft,
            monthsLeftAfter = after.size,
            interestLeftBefore = interestBefore,
            interestLeftAfter = after.map { it.interest }.sum(),
        )
    }

    /** Biggest loan a given EMI can carry: the EMI formula turned around. */
    fun maxPrincipal(
        emi: Money,
        annualRateBps: Int,
        months: Int,
    ): Money {
        require(emi.paise > 0 && months in 1..LoanTerms.MAX_TENURE_MONTHS && annualRateBps in 0..LoanTerms.MAX_RATE_BPS)
        if (annualRateBps == 0) return Money(Math.multiplyExact(emi.paise, months.toLong()))
        val r = monthlyRate(annualRateBps)
        val growth = BigDecimal.ONE.add(r).pow(months, PRECISION)
        val principal = BigDecimal(emi.paise).multiply(growth.subtract(BigDecimal.ONE)).divide(r.multiply(growth), PRECISION)
        return Money(principal.setScale(0, RoundingMode.HALF_UP).longValueExact())
    }

    /**
     * "Can I afford this loan?" (PRD F9d). Burden = all monthly EMIs / monthly income.
     * Above [warnPercent] is risky, above 30% is a caution, otherwise comfortable.
     */
    fun affordability(
        monthlyIncome: Money,
        existingEmis: Money,
        newEmi: Money,
        warnPercent: Double = DEFAULT_WARN_PERCENT,
    ): Affordability {
        require(monthlyIncome.paise > 0) { "income must be more than ₹0" }
        val total = existingEmis + newEmi
        val percent = total.paise.toDouble() / monthlyIncome.paise * PERCENT
        val level =
            when {
                percent > warnPercent -> BurdenLevel.RISKY
                percent > CAUTION_FROM_PERCENT -> BurdenLevel.CAUTION
                else -> BurdenLevel.COMFORTABLE
            }
        return Affordability(newEmi, total, percent, level)
    }

    /** One loop for every table: a fixed [months] length, or until the loan is cleared when null. */
    private fun amortize(
        start: Money,
        annualRateBps: Int,
        emi: Money,
        months: Int?,
    ): List<Installment> {
        val rows = ArrayList<Installment>()
        var balance = start.paise
        var number = 0
        while (balance > 0) {
            number++
            check(number <= SAFETY_LIMIT) { "instalment is too small to ever repay this loan" }
            val interest = divideRound(balance * annualRateBps, MONTHLY_DENOMINATOR)
            val isLast = number == months || balance + interest <= emi.paise
            val principal = if (isLast) balance else emi.paise - interest
            check(principal > 0) { "instalment does not even cover the interest" }
            balance -= principal
            rows += Installment(number, Money(principal + interest), Money(interest), Money(principal), Money(balance))
            if (months != null && number == months) break
        }
        return rows
    }

    private fun monthlyRate(annualRateBps: Int): BigDecimal =
        BigDecimal(annualRateBps).divide(BigDecimal(MONTHLY_DENOMINATOR), PRECISION)

    /** Integer division rounding half up (both sides positive here). */
    private fun divideRound(
        numerator: Long,
        denominator: Long,
    ): Long = (numerator * 2 + denominator) / (denominator * 2)
}
