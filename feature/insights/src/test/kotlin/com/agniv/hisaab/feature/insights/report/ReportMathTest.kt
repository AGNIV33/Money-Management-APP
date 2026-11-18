package com.agniv.hisaab.feature.insights.report

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

class ReportMathTest {
    private fun e(
        rupees: Long,
        category: String,
        date: LocalDate = LocalDate.of(2026, 11, 10),
        deleted: Boolean = false,
    ) = Expense(
        id = "$category$rupees$date",
        familyId = "f",
        paidBy = "m",
        ownerUserId = "u",
        amount = Money.ofRupees(rupees),
        categoryId = category,
        date = date,
        note = null,
        mode = PayMode.UPI,
        visibility = Visibility.FAMILY,
        source = Source.MANUAL,
        recurringId = null,
        updatedAt = Instant.EPOCH,
        deleted = deleted,
    )

    @Test
    fun `slices keep the biggest and fold the rest into Other`() {
        val list = listOf(e(500, "food"), e(300, "travel"), e(100, "bills"), e(60, "fun"), e(40, "misc"))
        val slices = ReportMath.slices(list, top = 3)
        assertThat(slices.map { it.categoryId }).containsExactly("food", "travel", "bills", null).inOrder()
        assertThat(slices.last().total).isEqualTo(Money.ofRupees(100))
        assertThat(slices.map { it.percent }).containsExactly(50, 30, 10, 10).inOrder()
    }

    @Test
    fun `month filter skips other months and deleted rows`() {
        val list = listOf(e(100, "a"), e(50, "a", deleted = true), e(70, "a", date = LocalDate.of(2026, 10, 31)))
        assertThat(ReportMath.total(ReportMath.inMonth(list, YearMonth.of(2026, 11)))).isEqualTo(Money.ofRupees(100))
    }

    @Test
    fun `change and share are whole percents, null without a base`() {
        assertThat(ReportMath.changeVsLast(Money.ofRupees(88), Money.ofRupees(100))).isEqualTo(-12)
        assertThat(ReportMath.changeVsLast(Money.ofRupees(88), Money.ZERO)).isNull()
        assertThat(ReportMath.share(Money.ofRupees(14_400), Money.ofRupees(60_000))).isEqualTo(24)
        assertThat(ReportMath.share(Money.ofRupees(1), null)).isNull()
    }

    @Test
    fun `tips name a big category and a friendly target`() {
        val tips = ReportMath.tips(listOf(Slice("shopping", Money.ofRupees(20), 28)), savedPercent = 36, change = -12)
        assertThat(tips).containsExactly(Tip.BigShare("shopping", 28), Tip.KeepUnder("shopping", 20), Tip.Saved(36)).inOrder()
    }

    @Test
    fun `axis steps are round numbers`() {
        assertThat(niceStep(Money.ofRupees(24_500).paise)).isEqualTo(Money.ofRupees(10_000).paise)
        assertThat(niceStep(Money.ofRupees(3_000).paise)).isEqualTo(Money.ofRupees(1_000).paise)
    }
}
