package com.agniv.hisaab.core.ml.insight

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.RecurRule
import com.agniv.hisaab.core.domain.model.Recurring
import com.agniv.hisaab.core.domain.model.Source
import com.agniv.hisaab.core.domain.model.Visibility
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class InsightEngineTest {
    private val today = LocalDate.of(2026, 11, 20)
    private var next = 0

    private fun e(
        date: LocalDate,
        rupees: Long,
        category: String = "food",
        source: Source = Source.MANUAL,
        deleted: Boolean = false,
    ) = Expense(
        "e${next++}",
        "f1",
        "m1",
        "u1",
        Money.ofRupees(rupees),
        category,
        date,
        null,
        PayMode.UPI,
        Visibility.FAMILY,
        source,
        null,
        Instant.EPOCH,
        deleted,
    )

    private fun compute(
        expenses: List<Expense>,
        budget: Money? = null,
        emi: Money = Money.ZERO,
        repeating: List<Recurring> = emptyList(),
    ) = InsightEngine.compute(InsightInput(today, expenses, budget, emi, repeating))

    private inline fun <reified T : Insight> List<Insight>.one(): T? = filterIsInstance<T>().firstOrNull()

    @Test
    fun `nothing to say with no data`() {
        assertThat(compute(emptyList())).isEmpty()
        assertThat(compute(listOf(e(today, 100, deleted = true)))).isEmpty()
    }

    @Test
    fun `month change compares the same days of both months`() {
        val data =
            listOf(
                e(LocalDate.of(2026, 11, 3), 3_000),
                e(LocalDate.of(2026, 11, 15), 2_000),
                e(LocalDate.of(2026, 10, 4), 2_000),
                e(LocalDate.of(2026, 10, 10), 2_000),
                // After day 20 of October: must not count against this month's first 20 days.
                e(LocalDate.of(2026, 10, 28), 9_000),
            )
        val c = compute(data).one<Insight.MonthChange>()
        assertThat(c?.spent).isEqualTo(Money.ofRupees(5_000))
        assertThat(c?.lastSamePeriod).isEqualTo(Money.ofRupees(4_000))
        assertThat(c?.percent).isEqualTo(25)
    }

    @Test
    fun `small changes and tiny bases are ignored`() {
        val same = listOf(e(LocalDate.of(2026, 11, 3), 1_000), e(LocalDate.of(2026, 10, 3), 980))
        assertThat(compute(same).one<Insight.MonthChange>()).isNull()
        val tiny = listOf(e(LocalDate.of(2026, 11, 3), 1_000), e(LocalDate.of(2026, 10, 3), 100))
        assertThat(compute(tiny).one<Insight.MonthChange>()).isNull()
    }

    @Test
    fun `a category that jumped is named`() {
        val data =
            listOf(
                e(LocalDate.of(2026, 11, 5), 3_000, "shopping"),
                e(LocalDate.of(2026, 11, 6), 500, "food"),
                e(LocalDate.of(2026, 10, 5), 1_000, "shopping"),
                e(LocalDate.of(2026, 10, 6), 500, "food"),
            )
        val j = compute(data).one<Insight.CategoryJump>()
        assertThat(j?.categoryId).isEqualTo("shopping")
        assertThat(j?.percent).isEqualTo(200)
    }

    @Test
    fun `one payment far above normal for its category is flagged, normal ones are not`() {
        val history = (1..8).map { e(LocalDate.of(2026, 10, it), 200 + it * 5L, "fuel") }
        val big = e(LocalDate.of(2026, 11, 10), 2_500, "fuel")
        val normal = e(LocalDate.of(2026, 11, 11), 230, "fuel")
        val found = compute(history + big + normal).one<Insight.BigExpense>()
        assertThat(found?.expenseId).isEqualTo(big.id)
        assertThat(compute(history + normal).one<Insight.BigExpense>()).isNull()
    }

    @Test
    fun `too little history, or a recurring payment, is not called unusual`() {
        val few = listOf(e(LocalDate.of(2026, 10, 1), 200, "fuel"), e(LocalDate.of(2026, 11, 10), 2_500, "fuel"))
        assertThat(compute(few).one<Insight.BigExpense>()).isNull()
        val history = (1..8).map { e(LocalDate.of(2026, 10, it), 200, "fuel") }
        val rent = e(LocalDate.of(2026, 11, 10), 2_500, "fuel", source = Source.RECURRING)
        assertThat(compute(history + rent).one<Insight.BigExpense>()).isNull()
    }

    @Test
    fun `same amount around the same day three months running is offered as a repeat`() {
        val data =
            listOf(
                e(LocalDate.of(2026, 9, 5), 12_000, "rent"),
                e(LocalDate.of(2026, 10, 6), 12_000, "rent"),
                e(LocalDate.of(2026, 11, 5), 12_000, "rent"),
            )
        val r = compute(data).one<Insight.RepeatCandidate>()
        assertThat(r?.categoryId).isEqualTo("rent")
        assertThat(r?.months).isEqualTo(3)
        assertThat(r?.dayOfMonth).isEqualTo(5)

        val already =
            Recurring(
                "r1",
                "f1",
                "u1",
                "m1",
                Money.ofRupees(12_000),
                "rent",
                PayMode.UPI,
                null,
                Visibility.FAMILY,
                RecurRule.MONTHLY,
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 11, 5),
                Instant.EPOCH,
            )
        assertThat(compute(data, repeating = listOf(already)).one<Insight.RepeatCandidate>()).isNull()
        // A different day each month is not a pattern.
        val scattered = listOf(e(LocalDate.of(2026, 9, 2), 500), e(LocalDate.of(2026, 10, 15), 500), e(LocalDate.of(2026, 11, 28), 500))
        assertThat(compute(scattered).one<Insight.RepeatCandidate>()).isNull()
    }

    @Test
    fun `month end projection and next month forecast`() {
        val data =
            listOf(
                e(LocalDate.of(2026, 11, 2), 10_000),
                e(LocalDate.of(2026, 10, 10), 30_000),
                e(LocalDate.of(2026, 9, 10), 24_000),
                e(LocalDate.of(2026, 8, 10), 18_000),
            )
        val projection = compute(data).one<Insight.MonthEndForecast>()
        assertThat(projection?.projected).isEqualTo(Money.ofRupees(15_000)) // 10,000 in 20 days -> 30 days
        val forecast = compute(data).one<Insight.NextMonthForecast>()
        assertThat(forecast?.forecast).isEqualTo(Money.ofRupees(24_000))
        assertThat(forecast?.basedOnMonths).isEqualTo(3)
    }

    @Test
    fun `budget pace says when the budget runs out`() {
        val data = listOf(e(LocalDate.of(2026, 11, 2), 20_000))
        // 20,000 in 20 days, 30,000 budget: runs out on day 30 at this pace? 1,000 a day -> day 30.
        val tight = compute(data, budget = Money.ofRupees(25_000)).one<Insight.BudgetPace>()
        assertThat(tight?.alreadyOver).isFalse()
        assertThat(tight?.crossesOnDay).isEqualTo(25)
        assertThat(tight?.safePerDay).isEqualTo(Money.ofRupees(500))

        val fine = compute(data, budget = Money.ofRupees(60_000)).one<Insight.BudgetPace>()
        assertThat(fine?.crossesOnDay).isNull()
        val over = compute(data, budget = Money.ofRupees(15_000)).one<Insight.BudgetPace>()
        assertThat(over?.alreadyOver).isTrue()
        // With a budget the plain month-end projection is replaced by the pace.
        assertThat(compute(data, budget = Money.ofRupees(60_000)).one<Insight.MonthEndForecast>()).isNull()
    }

    @Test
    fun `most urgent comes first and the list is capped`() {
        val data =
            listOf(
                e(LocalDate.of(2026, 11, 2), 20_000),
                e(LocalDate.of(2026, 10, 2), 5_000),
                e(LocalDate.of(2026, 9, 2), 5_000),
                e(LocalDate.of(2026, 8, 2), 5_000),
            )
        val all = compute(data, budget = Money.ofRupees(10_000), emi = Money.ofRupees(3_000))
        assertThat(all.first()).isInstanceOf(Insight.BudgetPace::class.java)
        assertThat(all.size).isAtMost(6)
        assertThat(all.map { it.priority }).isInOrder(compareByDescending<Int> { it })
    }

    @Test
    fun `emi load shows only when emis are a big share`() {
        val months = listOf(e(LocalDate.of(2026, 10, 2), 10_000), e(LocalDate.of(2026, 9, 2), 10_000), e(LocalDate.of(2026, 11, 2), 1_000))
        assertThat(compute(months, emi = Money.ofRupees(4_000)).one<Insight.EmiLoad>()?.percent).isEqualTo(40)
        assertThat(compute(months, emi = Money.ofRupees(1_000)).one<Insight.EmiLoad>()).isNull()
        assertThat(compute(months).one<Insight.EmiLoad>()).isNull()
    }
}
