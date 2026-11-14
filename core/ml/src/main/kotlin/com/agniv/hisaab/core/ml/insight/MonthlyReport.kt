package com.agniv.hisaab.core.ml.insight

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.domain.model.Expense
import java.time.LocalDate
import java.time.YearMonth

/** One category in the report card: this month so far against the same days of last month. */
data class CategoryRow(
    val categoryId: String,
    val thisMonth: Money,
    val lastSamePeriod: Money,
)

data class MonthTotal(
    val month: YearMonth,
    val total: Money,
)

/** A simple mood for the month, shown as an emoji. Never a judgement of the person, only of the pace. */
enum class ReportGrade { GOOD, OKAY, WATCH }

/** DESIGN.md 7.10 Monthly Report Card, worked out on the phone. */
data class MonthlyReport(
    val month: YearMonth,
    val spent: Money,
    val lastSamePeriod: Money,
    /** Null when last month is too small to compare fairly. */
    val changePercent: Int?,
    /** Biggest first, at most [MonthlyReportBuilder.MAX_ROWS]. */
    val categories: List<CategoryRow>,
    /** Everything after the top rows, this month so far. */
    val others: Money,
    /** Oldest first, ends with this month (partial). */
    val trend: List<MonthTotal>,
    /** Null when there is not enough to say yet. */
    val grade: ReportGrade?,
) {
    val isEmpty: Boolean get() = spent.paise <= 0 && lastSamePeriod.paise <= 0 && trend.all { it.total.paise <= 0 }
}

object MonthlyReportBuilder {
    const val MAX_ROWS = 6
    const val TREND_MONTHS = 6
    private const val MIN_COMPARE_PAISE = 50_000L
    private const val MIN_PACE_DAY = 5
    private const val OKAY_PERCENT = 15
    private const val PERCENT = 100

    /** [expenses] must already be filtered to what the viewer may see; deleted ones are ignored here. */
    fun build(
        today: LocalDate,
        expenses: List<Expense>,
        budget: Money?,
    ): MonthlyReport {
        val live = expenses.filter { !it.deleted }
        val month = YearMonth.from(today)
        val day = today.dayOfMonth
        val now = live.filter { YearMonth.from(it.date) == month && it.date.dayOfMonth <= day }
        val before = live.filter { YearMonth.from(it.date) == month.minusMonths(1) && it.date.dayOfMonth <= day }
        val spent = now.map { it.amount }.sum()
        val last = before.map { it.amount }.sum()

        val nowBy = now.groupBy { it.categoryId }.mapValues { (_, v) -> v.map { it.amount }.sum() }
        val beforeBy = before.groupBy { it.categoryId }.mapValues { (_, v) -> v.map { it.amount }.sum() }
        val rows =
            (nowBy.keys + beforeBy.keys)
                .map { CategoryRow(it, nowBy[it] ?: Money.ZERO, beforeBy[it] ?: Money.ZERO) }
                .sortedWith(compareByDescending<CategoryRow> { it.thisMonth.paise }.thenByDescending { it.lastSamePeriod.paise })
        val top = rows.take(MAX_ROWS)
        val others = rows.drop(MAX_ROWS).map { it.thisMonth }.sum()

        val trend =
            (TREND_MONTHS - 1 downTo 0).map { back ->
                val m = month.minusMonths(back.toLong())
                MonthTotal(m, live.filter { YearMonth.from(it.date) == m }.map { it.amount }.sum())
            }

        val change = if (last.paise >= MIN_COMPARE_PAISE) ((spent.paise - last.paise) * PERCENT / last.paise).toInt() else null
        return MonthlyReport(month, spent, last, change, top, others, trend, grade(budget, spent, change, today))
    }

    private fun grade(
        budget: Money?,
        spent: Money,
        change: Int?,
        today: LocalDate,
    ): ReportGrade? =
        if (budget != null && budget.paise > 0) budgetGrade(budget, spent, today) else changeGrade(change)

    private fun budgetGrade(
        budget: Money,
        spent: Money,
        today: LocalDate,
    ): ReportGrade? {
        val projected = spent.paise * today.lengthOfMonth() / today.dayOfMonth
        return when {
            spent >= budget -> ReportGrade.WATCH
            // Early in the month one big bill makes the pace look wild, so wait a few days before judging it.
            today.dayOfMonth < MIN_PACE_DAY -> null
            projected > budget.paise -> ReportGrade.OKAY
            else -> ReportGrade.GOOD
        }
    }

    private fun changeGrade(change: Int?): ReportGrade? =
        when {
            change == null -> null
            change <= 0 -> ReportGrade.GOOD
            change <= OKAY_PERCENT -> ReportGrade.OKAY
            else -> ReportGrade.WATCH
        }
}
