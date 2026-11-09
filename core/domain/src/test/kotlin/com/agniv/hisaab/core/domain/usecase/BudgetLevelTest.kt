package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.common.money.Money
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BudgetLevelTest {
    private val budget = Money.ofRupees(10_000)

    @Test
    fun `under 80 percent is ok`() {
        assertThat(budgetLevel(Money.ofRupees(7_999), budget)).isEqualTo(BudgetLevel.OK)
        assertThat(budgetLevel(Money.ZERO, budget)).isEqualTo(BudgetLevel.OK)
    }

    @Test
    fun `exactly 80 percent warns`() {
        assertThat(budgetLevel(Money.ofRupees(8_000), budget)).isEqualTo(BudgetLevel.WARNING)
        assertThat(budgetLevel(Money.ofRupees(9_999), budget)).isEqualTo(BudgetLevel.WARNING)
    }

    @Test
    fun `exactly 100 percent is over`() {
        assertThat(budgetLevel(Money.ofRupees(10_000), budget)).isEqualTo(BudgetLevel.OVER)
        assertThat(budgetLevel(Money.ofRupees(15_000), budget)).isEqualTo(BudgetLevel.OVER)
    }

    @Test
    fun `no budget never alerts`() {
        assertThat(budgetLevel(Money.ofRupees(500), Money.ZERO)).isEqualTo(BudgetLevel.OK)
    }

    @Test
    fun `paise precision at the edge`() {
        assertThat(budgetLevel(Money(799_999), budget)).isEqualTo(BudgetLevel.OK)
        assertThat(budgetLevel(Money(800_000), budget)).isEqualTo(BudgetLevel.WARNING)
    }

    @Test
    fun `fraction`() {
        assertThat(budgetFraction(Money.ofRupees(2_500), budget)).isWithin(0.0001f).of(0.25f)
        assertThat(budgetFraction(Money.ofRupees(1), Money.ZERO)).isEqualTo(0f)
    }
}
