package com.agniv.hisaab.core.domain.loan

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.sum
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Reference numbers were computed independently with Python's Decimal (60 digits), and the first four
 * match the usual bank calculators (₹8,678 / ₹10,747 / ₹8,333 / ₹8,885 style figures).
 */
class EmiMathTest {
    private fun terms(
        rupees: Long,
        bps: Int,
        months: Int,
    ) = LoanTerms(Money.ofRupees(rupees), bps, months)

    private fun m(
        rupees: Long,
        paise: Int,
    ) = Money.ofRupees(rupees, paise)

    @Test
    fun `emi matches reference values`() {
        assertThat(EmiMath.emi(terms(1_000_000, 850, 240))).isEqualTo(m(8_678, 23))
        assertThat(EmiMath.emi(terms(500_000, 1_050, 60))).isEqualTo(m(10_746, 95))
        assertThat(EmiMath.emi(terms(50_000, 1_400, 6))).isEqualTo(m(8_676, 90))
        assertThat(EmiMath.emi(terms(2_500_000, 900, 180))).isEqualTo(m(25_356, 66))
        assertThat(EmiMath.emi(terms(100_000, 1_200, 12))).isEqualTo(m(8_884, 88))
    }

    @Test
    fun `zero rate divides evenly and the last row adjusts`() {
        val t = terms(100_000, 0, 12)
        assertThat(EmiMath.emi(t)).isEqualTo(m(8_333, 33))
        val rows = EmiMath.schedule(t)
        assertThat(rows).hasSize(12)
        assertThat(rows.last().payment).isEqualTo(m(8_333, 37))
        assertThat(EmiMath.summary(t).totalInterest).isEqualTo(Money.ZERO)
    }

    @Test
    fun `schedule totals match reference`() {
        val s = EmiMath.summary(terms(1_000_000, 850, 240))
        assertThat(s.totalInterest).isEqualTo(m(1_082_776, 63))
        assertThat(s.totalPayable).isEqualTo(m(2_082_776, 63))
        assertThat(EmiMath.schedule(terms(1_000_000, 850, 240)).last().payment).isEqualTo(m(8_679, 66))
        assertThat(EmiMath.summary(terms(500_000, 1_050, 60)).totalInterest).isEqualTo(m(144_816, 99))
        assertThat(EmiMath.summary(terms(2_500_000, 900, 180)).totalInterest).isEqualTo(m(2_064_200, 54))
    }

    @Test
    fun `schedule invariants hold for many loans`() {
        val principals = listOf(1L, 999L, 50_000L, 1_000_000L, 7_777_777L, 250_000_000L)
        val rates = listOf(0, 1, 850, 1_050, 1_400, 2_400, 3_600, 4_200)
        val months = listOf(1, 2, 6, 12, 60, 240, 360, 480)
        val cases = principals.flatMap { p -> rates.flatMap { r -> months.map { n -> terms(p, r, n) } } }
        val repayable = cases.filter(EmiMath::isRepayable)
        repayable.forEach(::checkInvariants)
        // Everything a family could really take (up to 24%, 30 years, from ₹10,000) must be repayable.
        val realistic = cases.filter(::isRealistic)
        assertThat(realistic.all(EmiMath::isRepayable)).isTrue()
        assertThat(realistic).isNotEmpty()
    }

    // Up to 24%, 30 years, from ₹10,000: what a family could really take.
    private fun isRealistic(t: LoanTerms) =
        t.annualRateBps <= 2_400 && t.tenureMonths <= 360 && t.principal >= Money.ofRupees(10_000)

    private fun checkInvariants(t: LoanTerms) {
        val rows = EmiMath.schedule(t)
        // A rounded-up EMI on a toy loan (say ₹1 over 60 months) can finish early; real loans never do.
        assertThat(rows.size).isAtMost(t.tenureMonths)
        if (isRealistic(t)) assertThat(rows).hasSize(t.tenureMonths)
        // Principal parts add up to the loan; nothing is ever negative; the end is exactly zero.
        assertThat(rows.map { it.principal }.sum()).isEqualTo(t.principal)
        assertThat(rows.all { it.balance.paise >= 0 && it.principal.paise > 0 && it.interest.paise >= 0 }).isTrue()
        assertThat(rows.last().balance).isEqualTo(Money.ZERO)
        rows.forEach { assertThat(it.payment).isEqualTo(it.principal + it.interest) }
        rows.zipWithNext().forEach { (a, b) -> assertThat(b.balance).isLessThan(a.balance) }
    }

    @Test
    fun `rate is stored as basis points`() {
        assertThat(terms(100_000, 850, 12).annualRateBps).isEqualTo(850) // 8.5%
        // 0.01% (1 bp) is the smallest non-zero rate and still gives a valid table.
        assertThat(EmiMath.schedule(terms(100_000, 1, 12))).hasSize(12)
    }

    @Test
    fun `first month interest is balance times rate over twelve`() {
        val first = EmiMath.schedule(terms(1_000_000, 1_200, 12)).first()
        assertThat(first.interest).isEqualTo(Money.ofRupees(10_000)) // 12% / 12 = 1%
    }

    @Test
    fun `balance after n payments`() {
        val t = terms(1_000_000, 850, 240)
        assertThat(EmiMath.balanceAfter(t, 0)).isEqualTo(t.principal)
        assertThat(EmiMath.balanceAfter(t, 24)).isEqualTo(m(958_436, 29))
        assertThat(EmiMath.balanceAfter(t, 240)).isEqualTo(Money.ZERO)
    }

    @Test
    fun `prepaying and keeping the emi shortens the loan`() {
        val t = terms(1_000_000, 850, 240)
        val r = EmiMath.prepay(t, paidInstallments = 24, extra = Money.ofRupees(100_000), mode = PrepayMode.REDUCE_TENURE)
        assertThat(r.balanceBefore).isEqualTo(m(958_436, 29))
        assertThat(r.balanceAfter).isEqualTo(m(858_436, 29))
        assertThat(r.emiAfter).isEqualTo(r.emiBefore)
        assertThat(r.monthsLeftBefore).isEqualTo(216)
        assertThat(r.monthsLeftAfter).isEqualTo(171)
        assertThat(r.monthsSaved).isEqualTo(45)
        assertThat(r.interestLeftAfter).isEqualTo(m(624_602, 17))
        assertThat(r.interestSaved.paise).isGreaterThan(0)
    }

    @Test
    fun `prepaying and keeping the tenure lowers the emi`() {
        val t = terms(1_000_000, 850, 240)
        val r = EmiMath.prepay(t, 24, Money.ofRupees(100_000), PrepayMode.REDUCE_EMI)
        assertThat(r.monthsLeftAfter).isEqualTo(216)
        assertThat(r.emiAfter).isLessThan(r.emiBefore)
        assertThat(r.interestSaved.paise).isGreaterThan(0)
        // Shortening the loan saves more interest than lowering the EMI for the same lump sum.
        val shorter = EmiMath.prepay(t, 24, Money.ofRupees(100_000), PrepayMode.REDUCE_TENURE)
        assertThat(shorter.interestSaved).isGreaterThan(r.interestSaved)
    }

    @Test
    fun `prepay rejects nonsense`() {
        val t = terms(100_000, 1_000, 12)
        assertThrows(IllegalArgumentException::class.java) { EmiMath.prepay(t, 0, Money.ZERO, PrepayMode.REDUCE_EMI) }
        assertThrows(IllegalArgumentException::class.java) { EmiMath.prepay(t, 0, Money.ofRupees(100_000), PrepayMode.REDUCE_EMI) }
        assertThrows(IllegalArgumentException::class.java) { EmiMath.prepay(t, 12, Money.ofRupees(1), PrepayMode.REDUCE_EMI) }
    }

    @Test
    fun `max principal is the emi formula reversed`() {
        assertThat(EmiMath.maxPrincipal(Money.ofRupees(20_000), 900, 240)).isEqualTo(m(2_222_899, 8))
        val t = terms(1_000_000, 850, 240)
        val back = EmiMath.maxPrincipal(EmiMath.emi(t), 850, 240)
        assertThat((back - t.principal).abs().paise).isLessThan(Money.ofRupees(1).paise)
        assertThat(EmiMath.maxPrincipal(Money.ofRupees(5_000), 0, 10)).isEqualTo(Money.ofRupees(50_000))
    }

    @Test
    fun `affordability levels`() {
        val income = Money.ofRupees(100_000)
        val ok = EmiMath.affordability(income, existingEmis = Money.ofRupees(10_000), newEmi = Money.ofRupees(15_000))
        assertThat(ok.level).isEqualTo(BurdenLevel.COMFORTABLE)
        assertThat(ok.burdenPercent).isWithin(0.0001).of(25.0)

        val caution = EmiMath.affordability(income, Money.ofRupees(20_000), Money.ofRupees(15_000))
        assertThat(caution.level).isEqualTo(BurdenLevel.CAUTION)

        // Exactly 40% is still fine; above is risky (PRD: warn > 40%).
        assertThat(EmiMath.affordability(income, Money.ofRupees(25_000), Money.ofRupees(15_000)).level)
            .isEqualTo(BurdenLevel.CAUTION)
        assertThat(EmiMath.affordability(income, Money.ofRupees(25_000), Money.ofRupees(15_001)).level)
            .isEqualTo(BurdenLevel.RISKY)
        assertThat(EmiMath.affordability(income, Money.ofRupees(25_000), Money.ofRupees(15_000), warnPercent = 35.0).level)
            .isEqualTo(BurdenLevel.RISKY)
    }

    @Test
    fun `absurd rate and tenure combinations are flagged not crashed`() {
        val absurd = terms(100_000, 4_200, 480)
        assertThat(EmiMath.isRepayable(absurd)).isFalse()
        assertThrows(IllegalArgumentException::class.java) { EmiMath.schedule(absurd) }
        assertThat(EmiMath.isRepayable(terms(2_500_000, 900, 180))).isTrue()
    }

    @Test
    fun `invalid loans are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { terms(0, 850, 12) }
        assertThrows(IllegalArgumentException::class.java) { terms(1_000, -1, 12) }
        assertThrows(IllegalArgumentException::class.java) { terms(1_000, 4_201, 12) }
        assertThrows(IllegalArgumentException::class.java) { terms(1_000, 850, 0) }
        assertThrows(IllegalArgumentException::class.java) { terms(1_000, 850, 481) }
        assertThrows(IllegalArgumentException::class.java) { EmiMath.affordability(Money.ZERO, Money.ZERO, Money.ofRupees(1)) }
    }

    @Test
    fun `one month loan pays principal plus one month of interest`() {
        val rows = EmiMath.schedule(terms(12_000, 1_200, 1))
        assertThat(rows.single().payment).isEqualTo(Money.ofRupees(12_120))
    }
}
