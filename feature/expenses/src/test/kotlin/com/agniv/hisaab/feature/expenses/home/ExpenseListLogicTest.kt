package com.agniv.hisaab.feature.expenses.home

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.Source
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.feature.expenses.add.toInputText
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ExpenseListLogicTest {
    private val milk = Category("milk", null, "Milk", "दूध", "water_drop", 0, true)
    private val food = Category("food", null, "Food Outside", "बाहर खाना", "restaurant", 0, true)
    private val cats = mapOf(milk.id to milk, food.id to food)

    private fun e(
        id: String,
        cat: String,
        day: Int,
        rupees: Long,
        note: String? = null,
        mode: PayMode = PayMode.UPI,
    ) = Expense(
        id = id,
        familyId = "f",
        paidBy = "m",
        ownerUserId = "u",
        amount = Money.ofRupees(rupees),
        categoryId = cat,
        date = LocalDate.of(2026, 11, day),
        note = note,
        mode = mode,
        visibility = Visibility.FAMILY,
        source = Source.MANUAL,
        recurringId = null,
        updatedAt = Instant.EPOCH,
    )

    private val list =
        listOf(
            e("1", "milk", 9, 60, "Amul"),
            e("2", "food", 9, 450, "Swiggy dinner", PayMode.CARD),
            e("3", "milk", 7, 60),
        )

    @Test
    fun `no filter keeps everything`() {
        assertThat(list.applyFilter(ExpenseFilter(), cats)).hasSize(3)
    }

    @Test
    fun `search matches note, english and hindi category`() {
        assertThat(list.applyFilter(ExpenseFilter(query = "swiggy"), cats).map { it.id }).containsExactly("2")
        assertThat(list.applyFilter(ExpenseFilter(query = "MILK"), cats).map { it.id }).containsExactly("1", "3")
        assertThat(list.applyFilter(ExpenseFilter(query = "दूध"), cats).map { it.id }).containsExactly("1", "3")
    }

    @Test
    fun `category and mode filters combine`() {
        assertThat(list.applyFilter(ExpenseFilter(categoryId = "milk", mode = PayMode.CARD), cats)).isEmpty()
        assertThat(list.applyFilter(ExpenseFilter(mode = PayMode.CARD), cats).map { it.id }).containsExactly("2")
    }

    @Test
    fun `group by day newest first with totals`() {
        val days = list.groupByDay()
        assertThat(days.map { it.date.dayOfMonth }).containsExactly(9, 7).inOrder()
        assertThat(days.first().total).isEqualTo(Money.ofRupees(510))
    }

    @Test
    fun `money to keypad text`() {
        assertThat(Money(12_500).toInputText()).isEqualTo("125")
        assertThat(Money(12_550).toInputText()).isEqualTo("125.5")
        assertThat(Money(12_505).toInputText()).isEqualTo("125.05")
    }
}
