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
}
