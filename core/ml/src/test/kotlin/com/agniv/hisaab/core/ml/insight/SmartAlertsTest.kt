package com.agniv.hisaab.core.ml.insight

import com.agniv.hisaab.core.common.money.Money
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SmartAlertsTest {
    private val today = LocalDate.of(2026, 11, 20)
    private val r = Money.ofRupees(1000)

    @Test
    fun `only alert kinds become notifications`() {
        val insights =
            listOf(
                Insight.MonthChange(r, r, 20),
                Insight.NextMonthForecast(r, 3),
                Insight.MonthEndForecast(r, r),
                Insight.RepeatCandidate("rent", r, 5, 3),
                Insight.EmiLoad(r, r, 50),
                Insight.CategoryJump("food", r, r, 60),
                Insight.BigExpense("e1", "shop", r, r),
            )
        val alerts = SmartAlerts.pick(insights, today, Money.ZERO)
        assertThat(alerts.map { it.key }).containsExactly("jump:2026-11:food", "big:2026-11:e1")
    }

    @Test
    fun `budget alert only when it will run out, not when already over`() {
        fun pace(
            over: Boolean,
            day: Int?,
        ) = Insight.BudgetPace(r, r, r, over, day, 10, r)
        assertThat(SmartAlerts.pick(listOf(pace(false, 25)), today, Money.ZERO).single())
            .isEqualTo(SmartAlert.BudgetRunsOut(25, r, "pace:2026-11"))
        assertThat(SmartAlerts.pick(listOf(pace(true, null)), today, Money.ZERO)).isEmpty()
        assertThat(SmartAlerts.pick(listOf(pace(false, null)), today, Money.ZERO)).isEmpty()
    }

    @Test
    fun `report ready only in the first days and only after a month with spending`() {
        val early = LocalDate.of(2026, 12, 2)
        assertThat(SmartAlerts.pick(emptyList(), early, r).single())
            .isEqualTo(SmartAlert.ReportReady(YearMonth.of(2026, 11), "report:2026-12"))
        assertThat(SmartAlerts.pick(emptyList(), early, Money.ZERO)).isEmpty()
        assertThat(SmartAlerts.pick(emptyList(), LocalDate.of(2026, 12, 4), r)).isEmpty()
    }

    @Test
    fun `keys carry the month second so old ones can be dropped`() {
        val keys = SmartAlerts.pick(listOf(Insight.CategoryJump("food", r, r, 60)), LocalDate.of(2026, 12, 1), r).map { it.key }
        keys.forEach { assertThat(it.split(':')[1]).isEqualTo("2026-12") }
    }
}
