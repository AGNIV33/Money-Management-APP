package com.agniv.hisaab.feature.tools

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.domain.loan.EmiMath
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToolsLogicTest {
    @Test
    fun `terms need all three fields and a repayable loan`() {
        assertThat(ToolsLogic.terms("1000000", "8.5", "20", inYears = true)?.tenureMonths).isEqualTo(240)
        assertThat(ToolsLogic.terms("1000000", "", "20", inYears = true)).isNull()
        assertThat(ToolsLogic.terms("100000", "42", "40", inYears = true)).isNull()
    }

    @Test
    fun `yearly rows add up to the whole schedule`() {
        val terms = checkNotNull(ToolsLogic.terms("1000000", "8.5", "20", inYears = true))
        val schedule = EmiMath.schedule(terms)
        val years = ToolsLogic.yearly(schedule)
        assertThat(years).hasSize(20)
        assertThat(years.map { it.principal }.sum()).isEqualTo(terms.principal)
        assertThat(years.map { it.interest }.sum()).isEqualTo(schedule.map { it.interest }.sum())
        assertThat(years.last().balance).isEqualTo(Money.ZERO)
        // Early years are mostly interest, late years mostly principal.
        assertThat(years.first().interest).isGreaterThan(years.first().principal)
        assertThat(years.last().principal).isGreaterThan(years.last().interest)
    }

    @Test
    fun `affordable loan respects the 40 percent line`() {
        val income = Money.ofRupees(100_000)
        val max = checkNotNull(ToolsLogic.maxAffordableLoan(income, Money.ofRupees(10_000), 900, 240))
        // ₹30,000 a month of room at 9% for 20 years.
        assertThat(max).isEqualTo(EmiMath.maxPrincipal(Money.ofRupees(30_000), 900, 240))
        assertThat(ToolsLogic.maxAffordableLoan(income, Money.ofRupees(40_000), 900, 240)).isNull()
    }

    @Test
    fun `amount fields keep digits and one dot with two decimals`() {
        assertThat(AmountInput.cleanAmount("12,00,000")).isEqualTo("1200000")
        assertThat(AmountInput.cleanAmount("1.2.345")).isEqualTo("1.23")
        assertThat(AmountInput.cleanAmount("₹ 50k")).isEqualTo("50")
    }

    @Test
    fun `indian grouping puts commas after the last three digits then every two`() {
        assertThat(AmountInput.groupIndian("999")).isEqualTo("999")
        assertThat(AmountInput.groupIndian("1200000")).isEqualTo("12,00,000")
        assertThat(AmountInput.groupIndian("123456789.5")).isEqualTo("12,34,56,789.5")
    }

    @Test
    fun `fd adds interest every quarter and simple interest for leftover months`() {
        val lakh = Money.ofRupees(100_000)
        // 7% for a year: 1.0175^4.
        assertThat(ToolsLogic.lumpSumMaturity(lakh, 700, 12)).isEqualTo(Money(10_718_590))
        // 11 months: three quarters, then two months simple.
        assertThat(ToolsLogic.lumpSumMaturity(lakh, 700, 11)).isEqualTo(Money(10_657_141))
        assertThat(ToolsLogic.lumpSumMaturity(lakh, 0, 24)).isEqualTo(lakh)
    }

    @Test
    fun `monthly saving grows like a sip`() {
        assertThat(ToolsLogic.monthlyMaturity(Money.ofRupees(1_000), 1_200, 12)).isEqualTo(Money(1_280_933))
        assertThat(ToolsLogic.monthlyMaturity(Money.ofRupees(1_000), 0, 12)).isEqualTo(Money.ofRupees(12_000))
    }

    @Test
    fun `too big an answer is null, not a wrapped number`() {
        assertThat(ToolsLogic.monthlyMaturity(Money(100_000_000_000), 4_200, 480)).isNull()
    }
}
