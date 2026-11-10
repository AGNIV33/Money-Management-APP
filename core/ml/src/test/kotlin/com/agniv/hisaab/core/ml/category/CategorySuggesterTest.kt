package com.agniv.hisaab.core.ml.category

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.Source
import com.agniv.hisaab.core.domain.model.Visibility
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CategorySuggesterTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val ids = mapOf("Milk" to "c-milk", "Vegetables" to "c-veg", "Fuel" to "c-fuel", "Groceries" to "c-groc")
    private var n = 0

    private fun past(
        note: String?,
        category: String,
        deleted: Boolean = false,
    ) = Expense(
        "e${n++}",
        "f1",
        "m1",
        "u1",
        Money.ofRupees(100),
        category,
        today.minusDays(5),
        note,
        PayMode.UPI,
        Visibility.FAMILY,
        Source.MANUAL,
        null,
        Instant.EPOCH,
        deleted,
    )

    private fun suggest(
        note: String,
        history: List<Expense> = emptyList(),
    ) = CategorySuggester.suggest(note, history, ids, today)

    @Test
    fun `built in words work with no history`() {
        assertThat(suggest("doodh")).isEqualTo("c-milk")
        assertThat(suggest("petrol bike")).isEqualTo("c-fuel")
        assertThat(suggest("something odd")).isNull()
        assertThat(suggest("  ")).isNull()
    }

    @Test
    fun `your own habit beats the built in guess`() {
        // "bhaiya" has no built-in meaning, but you always file it under Vegetables.
        val history = listOf(past("bhaiya", "c-veg"), past("sabzi bhaiya", "c-veg"), past("bhaiya ji", "c-veg"))
        assertThat(suggest("bhaiya", history)).isEqualTo("c-veg")
        // You file "doodh" under Groceries on purpose: that wins over the built-in Milk.
        val milkAsGroceries = listOf(past("doodh", "c-groc"), past("doodh packet", "c-groc"))
        assertThat(suggest("doodh", milkAsGroceries)).isEqualTo("c-groc")
    }

    @Test
    fun `a word used once, or used for different things, is not a habit`() {
        assertThat(suggest("bhaiya", listOf(past("bhaiya", "c-veg")))).isNull()
        val mixed = listOf(past("bhaiya", "c-veg"), past("bhaiya", "c-milk"), past("bhaiya", "c-fuel"))
        assertThat(suggest("bhaiya", mixed)).isNull()
    }

    @Test
    fun `deleted and note-less entries teach nothing`() {
        val history = listOf(past("bhaiya", "c-veg", deleted = true), past("bhaiya", "c-veg", deleted = true), past(null, "c-veg"))
        assertThat(suggest("bhaiya", history)).isNull()
    }

    @Test
    fun `several words vote, the more used one wins`() {
        val history = List(4) { past("ramesh store", "c-groc") } + List(2) { past("ramesh bhaiya", "c-veg") }
        // "ramesh" is split 4 / 2 (67%, not a habit); "store" is clearly groceries.
        assertThat(suggest("ramesh store", history)).isEqualTo("c-groc")
    }
}
