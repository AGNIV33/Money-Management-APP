package com.agniv.hisaab.core.domain.loan

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Loan
import com.agniv.hisaab.core.domain.model.LoanType
import com.agniv.hisaab.core.domain.model.Visibility
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class LoanProgressTest {
    private fun loan(
        first: LocalDate = LocalDate.of(2026, 1, 5),
        addedOn: LocalDate = LocalDate.of(2026, 1, 1),
        months: Int = 12,
        dueDay: Int = first.dayOfMonth,
    ) = Loan(
        id = "l1",
        familyId = "f1",
        ownerUserId = "u1",
        type = LoanType.CAR,
        lender = "SBI",
        principal = Money.ofRupees(100_000),
        annualRateBps = 1_200,
        tenureMonths = months,
        emi = Money.ofRupees(8_885),
        firstEmiDate = first,
        dueDay = dueDay,
        addedOn = addedOn,
        visibility = Visibility.FAMILY,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `due day is clamped to short months`() {
        val l = loan(first = LocalDate.of(2026, 1, 31))
        assertThat(LoanProgress.dueDate(l, 2)).isEqualTo(LocalDate.of(2026, 2, 28))
        assertThat(LoanProgress.dueDate(l, 3)).isEqualTo(LocalDate.of(2026, 3, 31))
        assertThat(LoanProgress.dueDate(l, 4)).isEqualTo(LocalDate.of(2026, 4, 30))
    }

    @Test
    fun `nothing paid yet, first emi soon`() {
        val p = LoanProgress.of(loan(), emptySet(), today = LocalDate.of(2026, 1, 3))
        assertThat(p.paidCount).isEqualTo(0)
        assertThat(p.outstanding).isEqualTo(Money.ofRupees(100_000))
        assertThat(p.next?.number).isEqualTo(1)
        assertThat(p.next?.daysLeft).isEqualTo(2)
        assertThat(p.next?.status).isEqualTo(DueStatus.SOON)
        assertThat(p.overdueCount).isEqualTo(0)
    }

    @Test
    fun `unmarked past emi is overdue, marking it moves to the next`() {
        val today = LocalDate.of(2026, 1, 10)
        val unpaid = LoanProgress.of(loan(), emptySet(), today)
        assertThat(unpaid.next?.status).isEqualTo(DueStatus.OVERDUE)
        assertThat(unpaid.next?.daysLeft).isEqualTo(-5)
        assertThat(unpaid.overdueCount).isEqualTo(1)

        val paid = LoanProgress.of(loan(), setOf(YearMonth.of(2026, 1)), today)
        assertThat(paid.paidCount).isEqualTo(1)
        assertThat(paid.next?.date).isEqualTo(LocalDate.of(2026, 2, 5))
        assertThat(paid.next?.status).isEqualTo(DueStatus.LATER)
        assertThat(paid.outstanding).isEqualTo(EmiMath.balanceAfter(loan().terms, 1))
    }

    @Test
    fun `emis due before the loan was added count as paid`() {
        // Started Jan, added to the app on 20 Jun: Jan..Jun are history, Jul is the next one.
        val l = loan(addedOn = LocalDate.of(2026, 6, 20))
        val p = LoanProgress.of(l, emptySet(), today = LocalDate.of(2026, 6, 20))
        assertThat(p.paidCount).isEqualTo(6)
        assertThat(p.next?.date).isEqualTo(LocalDate.of(2026, 7, 5))
        assertThat(p.overdueCount).isEqualTo(0)
    }

    @Test
    fun `due today`() {
        val p = LoanProgress.of(loan(), emptySet(), today = LocalDate.of(2026, 1, 5))
        assertThat(p.next?.status).isEqualTo(DueStatus.TODAY)
    }

    @Test
    fun `all paid closes the loan`() {
        val months = (1..12).map { YearMonth.of(2026, it) }.toSet()
        val p = LoanProgress.of(loan(), months, today = LocalDate.of(2026, 12, 6))
        assertThat(p.isClosed).isTrue()
        assertThat(p.outstanding).isEqualTo(Money.ZERO)
        assertThat(p.paidFraction).isEqualTo(1f)
    }
}
