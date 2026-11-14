package com.agniv.hisaab.core.ml.insight

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.Source
import com.agniv.hisaab.core.domain.model.Visibility
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class MonthlyReportTest {
    private val today = LocalDate.of(2026, 11, 20)
    private var next = 0

    private fun e(
        date: LocalDate,
        rupees: Long,
        category: String = "food",
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
        Source.MANUAL,
        null,
        Instant.EPOCH,
        deleted,
    )

    @Test
    fun `compares only the same days of last month`() {
        val list =
            listOf(
                e(today.withDayOfMonth(3), 1_200),
                e(LocalDate.of(2026, 10, 10), 1_000),
                // After day 20 of last month: not part of the fair comparison.
                e(LocalDate.of(2026, 10, 25), 5_000),
            )
        val r = MonthlyReportBuilder.build(today, list, null)
        assertThat(r.spent).isEqualTo(Money.ofRupees(1_200))
        assertThat(r.lastSamePeriod).isEqualTo(Money.ofRupees(1_000))
        assertThat(r.changePercent).isEqualTo(20)
        assertThat(r.grade).isEqualTo(ReportGrade.WATCH)
    }

    @Test
    fun `categories sorted biggest first, rest go to others, deleted ignored`() {
        val list =
            (1..8).map { e(today.withDayOfMonth(it), it * 100L, "c$it") } +
                e(today.withDayOfMonth(2), 9_999, "c1", deleted = true) +
                e(LocalDate.of(2026, 10, 2), 300, "old")
        val r = MonthlyReportBuilder.build(today, list, null)
        assertThat(r.categories.map { it.categoryId }).containsExactly("c8", "c7", "c6", "c5", "c4", "c3").inOrder()
        // c2 (200) + c1 (100) this month; "old" had nothing this month.
        assertThat(r.others).isEqualTo(Money.ofRupees(300))
    }

    @Test
    fun `category only spent last month still shows with zero now`() {
        val list = listOf(e(today.withDayOfMonth(5), 500, "food"), e(LocalDate.of(2026, 10, 5), 800, "fuel"))
        val r = MonthlyReportBuilder.build(today, list, null)
        val fuel = r.categories.single { it.categoryId == "fuel" }
        assertThat(fuel.thisMonth).isEqualTo(Money.ZERO)
        assertThat(fuel.lastSamePeriod).isEqualTo(Money.ofRupees(800))
    }

    @Test
    fun `trend has six months oldest first ending this month`() {
        val list = listOf(e(LocalDate.of(2026, 6, 1), 700), e(LocalDate.of(2026, 5, 1), 999), e(today, 100))
        val r = MonthlyReportBuilder.build(today, list, null)
        assertThat(r.trend.map { it.month })
            .containsExactly(
                YearMonth.of(2026, 6),
                YearMonth.of(2026, 7),
                YearMonth.of(2026, 8),
                YearMonth.of(2026, 9),
                YearMonth.of(2026, 10),
                YearMonth.of(2026, 11),
            ).inOrder()
        assertThat(r.trend.first().total).isEqualTo(Money.ofRupees(700))
        assertThat(r.trend.last().total).isEqualTo(Money.ofRupees(100))
    }

    @Test
    fun `small last month gives no percent and no grade`() {
        val list = listOf(e(today, 1_000), e(LocalDate.of(2026, 10, 1), 100))
        val r = MonthlyReportBuilder.build(today, list, null)
        assertThat(r.changePercent).isNull()
        assertThat(r.grade).isNull()
    }

    @Test
    fun `grade follows the budget when one is set`() {
        val list = listOf(e(today.withDayOfMonth(2), 10_000))
        // 10,000 by day 20 of 30 -> about 15,000 by month end.
        assertThat(MonthlyReportBuilder.build(today, list, Money.ofRupees(20_000)).grade).isEqualTo(ReportGrade.GOOD)
        assertThat(MonthlyReportBuilder.build(today, list, Money.ofRupees(12_000)).grade).isEqualTo(ReportGrade.OKAY)
        assertThat(MonthlyReportBuilder.build(today, list, Money.ofRupees(9_000)).grade).isEqualTo(ReportGrade.WATCH)
    }

    @Test
    fun `less than last month is good, early month with budget waits`() {
        val list = listOf(e(today, 800), e(LocalDate.of(2026, 10, 1), 1_000))
        assertThat(MonthlyReportBuilder.build(today, list, null).grade).isEqualTo(ReportGrade.GOOD)
        val early = LocalDate.of(2026, 11, 2)
        assertThat(MonthlyReportBuilder.build(early, listOf(e(early, 100)), Money.ofRupees(20_000)).grade).isNull()
    }

    @Test
    fun `nothing at all is empty`() {
        assertThat(MonthlyReportBuilder.build(today, emptyList(), null).isEmpty).isTrue()
    }
}
