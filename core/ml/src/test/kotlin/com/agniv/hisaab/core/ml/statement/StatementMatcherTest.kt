package com.agniv.hisaab.core.ml.statement

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.loan.LoanProgress
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.Loan
import com.agniv.hisaab.core.domain.model.LoanType
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.Source
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.usecase.LoanItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class StatementMatcherTest {
    private fun line(
        day: Int,
        rupees: Long,
        text: String,
        debit: Boolean? = true,
    ) = StatementLine(LocalDate.of(2026, 11, day), text, Money.ofRupees(rupees), debit, null)

    private fun expense(
        id: String,
        day: Int,
        rupees: Long,
        deleted: Boolean = false,
    ) = Expense(
        id,
        "f1",
        "m1",
        "u1",
        Money.ofRupees(rupees),
        "c1",
        LocalDate.of(2026, 11, day),
        null,
        PayMode.UPI,
        Visibility.FAMILY,
        Source.MANUAL,
        null,
        Instant.EPOCH,
        deleted,
    )

    private fun loanItem(paid: Set<YearMonth> = emptySet()): LoanItem {
        val loan =
            Loan(
                "l1",
                "f1",
                "u1",
                LoanType.CAR,
                "HDFC",
                Money.ofRupees(100_000),
                1_200,
                12,
                Money.ofRupees(8_885),
                LocalDate.of(2026, 10, 5),
                5,
                LocalDate.of(2026, 10, 1),
                Visibility.FAMILY,
                Instant.EPOCH,
            )
        return LoanItem(loan, LoanProgress.of(loan, paid, LocalDate.of(2026, 11, 10)), paid)
    }

    private fun resultsFor(
        lines: List<StatementLine>,
        expenses: List<Expense> = emptyList(),
        loans: List<LoanItem> = emptyList(),
    ) = StatementMatcher.match(lines, expenses, loans).map { it.result }

    @Test
    fun `same amount within three days is already added`() {
        val r = resultsFor(listOf(line(5, 350, "UPI SWIGGY")), listOf(expense("e1", 4, 350)))
        assertThat((r.single() as MatchResult.Added).expense.id).isEqualTo("e1")
    }

    @Test
    fun `one expense can match only one payment`() {
        val r = resultsFor(listOf(line(5, 200, "chai"), line(6, 200, "chai again")), listOf(expense("e1", 5, 200)))
        assertThat(r[0]).isInstanceOf(MatchResult.Added::class.java)
        assertThat(r[1]).isInstanceOf(MatchResult.Missing::class.java)
    }

    @Test
    fun `further away is not sure, different amount or deleted is missing`() {
        assertThat(resultsFor(listOf(line(15, 350, "x")), listOf(expense("e1", 8, 350))).single())
            .isInstanceOf(MatchResult.Unsure::class.java)
        assertThat(resultsFor(listOf(line(5, 350, "x")), listOf(expense("e1", 5, 351))).single())
            .isInstanceOf(MatchResult.Missing::class.java)
        assertThat(resultsFor(listOf(line(5, 350, "x")), listOf(expense("e1", 5, 350, deleted = true))).single())
            .isInstanceOf(MatchResult.Missing::class.java)
    }

    @Test
    fun `missing payments get a category, mode and clean note`() {
        val missing =
            resultsFor(listOf(line(5, 120, "UPI/DR/ BIGBASKET"), line(6, 2000, "ATM WDL SBI"))).map { it as MatchResult.Missing }
        assertThat(missing[0].categoryName).isEqualTo("Groceries")
        assertThat(missing[0].mode).isEqualTo(PayMode.UPI)
        assertThat(missing[0].note).isEqualTo("Bigbasket")
        assertThat(missing[1].mode).isEqualTo(PayMode.CASH)
    }

    @Test
    fun `money received is ignored`() {
        assertThat(resultsFor(listOf(line(5, 45_000, "SALARY", debit = false)))).isEmpty()
        // Unknown direction is kept so the user can decide.
        assertThat(resultsFor(listOf(line(5, 100, "?", debit = null)))).hasSize(1)
    }

    @Test
    fun `an emi payment is recognised, marked or not`() {
        val emi = listOf(line(5, 8_885, "ACH D HDFC BANK LOAN"))
        val unmarked = resultsFor(emi, loans = listOf(loanItem())).single() as MatchResult.EmiUnmarked
        assertThat(unmarked.month).isEqualTo(YearMonth.of(2026, 11))
        assertThat(resultsFor(emi, loans = listOf(loanItem(setOf(YearMonth.of(2026, 11))))).single())
            .isInstanceOf(MatchResult.EmiMarked::class.java)
        // Same amount but far from the due day is just a payment.
        assertThat(resultsFor(listOf(line(25, 8_885, "x")), loans = listOf(loanItem())).single())
            .isInstanceOf(MatchResult.Missing::class.java)
    }

    @Test
    fun `summary counts each kind`() {
        val matched =
            StatementMatcher.match(
                listOf(line(5, 350, "a"), line(6, 999, "b"), line(7, 8_885, "emi")),
                listOf(expense("e1", 5, 350)),
                listOf(loanItem()),
            )
        val s = matched.summary()
        assertThat(listOf(s.added, s.emiToMark, s.unsure, s.missing)).containsExactly(1, 1, 0, 1).inOrder()
    }
}
