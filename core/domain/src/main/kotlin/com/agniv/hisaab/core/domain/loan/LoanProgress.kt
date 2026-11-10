package com.agniv.hisaab.core.domain.loan

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Loan
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class DueStatus { OVERDUE, TODAY, SOON, LATER }

data class NextEmi(
    val number: Int,
    val date: LocalDate,
    val month: YearMonth,
    /** Negative when overdue. */
    val daysLeft: Long,
    val status: DueStatus,
)

/** Where a loan stands today: what is paid, what is left, what is due next. */
data class LoanProgress(
    val paidCount: Int,
    val tenureMonths: Int,
    val outstanding: Money,
    /** Null once every instalment is paid. */
    val next: NextEmi?,
    val overdueCount: Int,
) {
    val isClosed: Boolean get() = next == null
    val paidFraction: Float get() = paidCount.toFloat() / tenureMonths

    companion object {
        /** EMI reminders start 3 days before (PRD G3). */
        const val SOON_DAYS = 3L

        fun dueDate(
            loan: Loan,
            number: Int,
        ): LocalDate {
            val month = YearMonth.from(loan.firstEmiDate).plusMonths((number - 1).toLong())
            return month.atDay(minOf(loan.dueDay, month.lengthOfMonth()))
        }

        /**
         * [paidMonths]: months that have a "paid" mark. Instalments due before [Loan.addedOn] count as
         * paid without one. Outstanding comes from the formula schedule, so it is the bank's figure
         * give or take the few rupees a rounded bank EMI adds.
         */
        fun of(
            loan: Loan,
            paidMonths: Set<YearMonth>,
            today: LocalDate,
        ): LoanProgress {
            val n = loan.tenureMonths
            val isPaid = BooleanArray(n) { i -> dueDate(loan, i + 1) < loan.addedOn || YearMonth.from(dueDate(loan, i + 1)) in paidMonths }
            val paidCount = isPaid.count { it }
            val nextIndex = isPaid.indexOfFirst { !it }
            val next =
                if (nextIndex < 0) {
                    null
                } else {
                    val date = dueDate(loan, nextIndex + 1)
                    val days = ChronoUnit.DAYS.between(today, date)
                    val status =
                        when {
                            days < 0 -> DueStatus.OVERDUE
                            days == 0L -> DueStatus.TODAY
                            days <= SOON_DAYS -> DueStatus.SOON
                            else -> DueStatus.LATER
                        }
                    NextEmi(nextIndex + 1, date, YearMonth.from(date), days, status)
                }
            val overdue = (0 until n).count { !isPaid[it] && dueDate(loan, it + 1) < today }
            val terms = loan.terms
            val outstanding =
                when {
                    paidCount == n -> Money.ZERO
                    EmiMath.isRepayable(terms) -> EmiMath.balanceAfter(terms, paidCount)
                    else -> loan.principal
                }
            return LoanProgress(paidCount, n, outstanding, next, overdue)
        }
    }
}
