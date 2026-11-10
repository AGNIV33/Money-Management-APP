package com.agniv.hisaab.core.domain.model

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.loan.LoanTerms
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

enum class LoanType { HOME, CAR, BIKE, PERSONAL, EDUCATION, GOLD, CONSUMER, CREDIT_CARD, OTHER }

/**
 * A loan the family is repaying. [firstEmiDate] is the date of instalment 1; every later instalment
 * falls on [dueDay] of the following months (clamped to the month's last day, e.g. 31 → 30 Nov).
 * [emi] is what the bank actually charges; it can differ by a few rupees from the formula.
 * [addedOn] is when the loan was entered in the app: instalments due before it are taken as already
 * paid (nobody back-fills years of history), later ones must be marked paid.
 */
data class Loan(
    val id: String,
    val familyId: String,
    val ownerUserId: String,
    val type: LoanType,
    val lender: String,
    val principal: Money,
    val annualRateBps: Int,
    val tenureMonths: Int,
    val emi: Money,
    val firstEmiDate: LocalDate,
    val dueDay: Int,
    val addedOn: LocalDate,
    val visibility: Visibility,
    val updatedAt: Instant,
    val deleted: Boolean = false,
) {
    val terms: LoanTerms get() = LoanTerms(principal, annualRateBps, tenureMonths)
}

/** One EMI marked as paid. [month] is the month the instalment was due in. */
data class LoanPayment(
    val id: String,
    val loanId: String,
    val familyId: String,
    val ownerUserId: String,
    val month: YearMonth,
    val amount: Money,
    val paidOn: LocalDate,
    val updatedAt: Instant,
    val deleted: Boolean = false,
)
