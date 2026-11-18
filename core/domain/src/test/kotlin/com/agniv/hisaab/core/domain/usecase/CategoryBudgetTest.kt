package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.common.money.Money
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CategoryBudgetTest {
    private fun rs(r: Long) = Money.ofRupees(r)

    @Test
    fun `only categories with a limit show, most used first`() {
        val list =
            categoryBudgets(
                limits = mapOf("food" to rs(5_000), "fuel" to rs(2_000), "fun" to rs(1_000)),
                spentByCategory = mapOf("food" to rs(4_000), "fuel" to rs(2_500), "rent" to rs(20_000)),
            )
        assertThat(list.map { it.categoryId }).containsExactly("fuel", "food", "fun").inOrder()
        assertThat(list.map { it.level }).containsExactly(BudgetLevel.OVER, BudgetLevel.WARNING, BudgetLevel.OK).inOrder()
        assertThat(list.last().spent).isEqualTo(Money.ZERO)
    }

    @Test
    fun `a zero limit is ignored`() {
        assertThat(categoryBudgets(mapOf("food" to Money.ZERO), mapOf("food" to rs(10)))).isEmpty()
    }
}
