@file:Suppress("MagicNumber") // priorities and thresholds are the tuning numbers of this file

package com.agniv.hisaab.core.ml.insight

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.Recurring
import com.agniv.hisaab.core.domain.model.Source
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.sqrt

/** Everything the engine needs, already filtered to what the viewer may see. */
data class InsightInput(
    val today: LocalDate,
    /** Live expenses, at least the last four months. */
    val expenses: List<Expense>,
    val budget: Money?,
    /** Sum of EMIs of loans still running. */
    val monthlyEmi: Money,
    /** Repeating expenses already set up, so they are not suggested again. */
    val repeating: List<Recurring>,
)

/**
 * What Kinly noticed. These are facts, not sentences: the screen turns them into words in English or Hindi.
 * Percent values are whole numbers, positive = more than before.
 */
sealed interface Insight {
    val priority: Int

    /** Spent so far this month compared with the same days of last month. */
    data class MonthChange(
        val spent: Money,
        val lastSamePeriod: Money,
        val percent: Int,
    ) : Insight {
        override val priority get() = 60
    }

    data class CategoryJump(
        val categoryId: String,
        val spent: Money,
        val lastSamePeriod: Money,
        val percent: Int,
    ) : Insight {
        override val priority get() = 70
    }

    /** One payment far above what is normal for its category. */
    data class BigExpense(
        val expenseId: String,
        val categoryId: String,
        val amount: Money,
        val typical: Money,
    ) : Insight {
        override val priority get() = 80
    }

    /** The same amount in the same category, around the same day, three months in a row: could repeat by itself. */
    data class RepeatCandidate(
        val categoryId: String,
        val amount: Money,
        val dayOfMonth: Int,
        val months: Int,
    ) : Insight {
        override val priority get() = 50
    }

    /** Where this month will end if spending goes on at the same pace. */
    data class MonthEndForecast(
        val spentSoFar: Money,
        val projected: Money,
    ) : Insight {
        override val priority get() = 40
    }

    /** Average of the last full months, as a guess for next month. */
    data class NextMonthForecast(
        val forecast: Money,
        val basedOnMonths: Int,
    ) : Insight {
        override val priority get() = 30
    }

    /**
     * Budget burn rate. [crossesOnDay] is the day of this month the budget runs out at the current pace,
     * null when it already has or will not. [safePerDay] is what can still be spent each remaining day.
     */
    data class BudgetPace(
        val budget: Money,
        val spent: Money,
        val projected: Money,
        val alreadyOver: Boolean,
        val crossesOnDay: Int?,
        val daysLeft: Int,
        val safePerDay: Money,
    ) : Insight {
        override val priority get() = if (alreadyOver || crossesOnDay != null) 100 else 35
    }

    /** How much of a normal month's spending goes to EMIs. */
    data class EmiLoad(
        val emi: Money,
        val averageMonthly: Money,
        val percent: Int,
    ) : Insight {
        override val priority get() = 55
    }
}

/**
 * Plain statistics on the phone (PRD F17, ARCHITECTURE.md 6.1): no network, no AI, works offline.
 * Money is added and compared as paise; only the spike test uses floating point, and only to decide whether
 * something is unusual, never to produce an amount.
 */
@Suppress("TooManyFunctions", "MagicNumber") // one small function per kind of insight; thresholds are the tuning knobs
object InsightEngine {
    private const val MAX_INSIGHTS = 6
    private const val MIN_COMPARE_PAISE = 50_000L
    private const val MIN_JUMP_PAISE = 50_000L
    private const val MIN_BIG_PAISE = 50_000L
    private const val MIN_REPEAT_PAISE = 10_000L
    private const val HISTORY_DAYS = 90L
    private const val MIN_HISTORY = 5
    private const val Z_LIMIT = 2.5
    private const val FLAT_MULTIPLE = 3
    private const val MIN_PERCENT_CHANGE = 10
    private const val MIN_JUMP_PERCENT = 40
    private const val MIN_PACE_DAY = 5
    private const val EMI_NOTICE_PERCENT = 30

    fun compute(input: InsightInput): List<Insight> {
        val live = input.expenses.filter { !it.deleted }
        if (live.isEmpty()) return emptyList()
        val today = input.today
        val month = YearMonth.from(today)
        val thisMonth = live.filter { YearMonth.from(it.date) == month }
        val results = mutableListOf<Insight>()
        results += monthChange(live, month, today.dayOfMonth)
        results += categoryJumps(live, month, today.dayOfMonth)
        results += bigExpenses(live, thisMonth, today)
        results += repeatCandidates(live, month, input.repeating)
        val spent = thisMonth.map { it.amount }.sum()
        val projected = projected(spent, today)
        results += budgetPace(input.budget, spent, projected, today)
        if (input.budget == null) results += monthEnd(spent, projected, today)
        val past = pastMonthTotals(live, month)
        results += nextMonth(past)
        results += emiLoad(input.monthlyEmi, past, projected)
        return results.sortedByDescending { it.priority }.take(MAX_INSIGHTS)
    }

    private fun spentIn(
        live: List<Expense>,
        month: YearMonth,
        upToDay: Int,
    ): Money = live.filter { YearMonth.from(it.date) == month && it.date.dayOfMonth <= upToDay }.map { it.amount }.sum()

    private fun percent(
        now: Money,
        before: Money,
    ): Int = ((now.paise - before.paise) * 100 / before.paise).toInt()

    private fun monthChange(
        live: List<Expense>,
        month: YearMonth,
        day: Int,
    ): List<Insight> {
        val spent = spentIn(live, month, day)
        val last = spentIn(live, month.minusMonths(1), day)
        if (spent.paise <= 0 || last.paise < MIN_COMPARE_PAISE) return emptyList()
        val p = percent(spent, last)
        return if (kotlin.math.abs(p) >= MIN_PERCENT_CHANGE) listOf(Insight.MonthChange(spent, last, p)) else emptyList()
    }

    private fun categoryJumps(
        live: List<Expense>,
        month: YearMonth,
        day: Int,
    ): List<Insight> {
        val now = live.filter { YearMonth.from(it.date) == month && it.date.dayOfMonth <= day }.groupBy { it.categoryId }
        val before = live.filter { YearMonth.from(it.date) == month.minusMonths(1) && it.date.dayOfMonth <= day }.groupBy { it.categoryId }
        return now
            .mapNotNull { (id, items) ->
                val spent = items.map { it.amount }.sum()
                val last = before[id].orEmpty().map { it.amount }.sum()
                if (spent.paise < MIN_JUMP_PAISE || last.paise <= 0) return@mapNotNull null
                val p = percent(spent, last)
                if (p >= MIN_JUMP_PERCENT) Insight.CategoryJump(id, spent, last, p) else null
            }.sortedByDescending { it.spent.paise - it.lastSamePeriod.paise }
            .take(2)
    }

    private fun bigExpenses(
        live: List<Expense>,
        thisMonth: List<Expense>,
        today: LocalDate,
    ): List<Insight> =
        thisMonth
            .filter { it.amount.paise >= MIN_BIG_PAISE && it.source != Source.RECURRING }
            .mapNotNull { e ->
                val history =
                    live
                        .filter {
                            it.id != e.id &&
                                it.categoryId == e.categoryId &&
                                it.date < e.date &&
                                it.date >= today.minusDays(HISTORY_DAYS)
                        }.map { it.amount.paise.toDouble() }
                if (history.size < MIN_HISTORY) return@mapNotNull null
                val mean = history.average()
                val std = sqrt(history.sumOf { (it - mean) * (it - mean) } / history.size)
                val unusual = if (std > 0) (e.amount.paise - mean) / std >= Z_LIMIT else e.amount.paise >= mean * FLAT_MULTIPLE
                if (unusual) Insight.BigExpense(e.id, e.categoryId, e.amount, Money(mean.toLong())) else null
            }.sortedByDescending { it.amount.paise }
            .take(2)

    private fun repeatCandidates(
        live: List<Expense>,
        month: YearMonth,
        repeating: List<Recurring>,
    ): List<Insight> {
        val since = month.minusMonths(3)
        return live
            .filter {
                it.recurringId == null &&
                    it.source != Source.RECURRING &&
                    YearMonth.from(it.date) >= since &&
                    it.amount.paise >= MIN_REPEAT_PAISE
            }.groupBy { it.categoryId to it.amount.paise }
            .mapNotNull { (key, items) ->
                val months = items.map { YearMonth.from(it.date) }.distinct()
                val days = items.map { it.date.dayOfMonth }
                val alreadySet = repeating.any { !it.deleted && it.categoryId == key.first && it.amount.paise == key.second }
                if (months.size < 3 || days.max() - days.min() > 5 || alreadySet) return@mapNotNull null
                Insight.RepeatCandidate(key.first, Money(key.second), days.sorted()[days.size / 2], months.size)
            }.sortedByDescending { it.amount.paise }
            .take(2)
    }

    private fun projected(
        spent: Money,
        today: LocalDate,
    ): Money {
        val len = today.lengthOfMonth()
        return Money(spent.paise * len / today.dayOfMonth)
    }

    private fun budgetPace(
        budget: Money?,
        spent: Money,
        projected: Money,
        today: LocalDate,
    ): List<Insight> {
        if (budget == null || budget.paise <= 0 || spent.paise <= 0) return emptyList()
        val daysLeft = today.lengthOfMonth() - today.dayOfMonth
        val over = spent >= budget
        val crosses =
            if (over || today.dayOfMonth < MIN_PACE_DAY || projected <= budget) {
                null
            } else {
                // The day the running total reaches the budget if every day looks like the days so far.
                ((budget.paise * today.dayOfMonth + spent.paise - 1) / spent.paise).toInt().coerceIn(
                    today.dayOfMonth + 1,
                    today.lengthOfMonth(),
                )
            }
        val safe = if (over || daysLeft <= 0) Money.ZERO else Money((budget.paise - spent.paise) / daysLeft)
        return listOf(Insight.BudgetPace(budget, spent, projected, over, crosses, daysLeft, safe))
    }

    private fun monthEnd(
        spent: Money,
        projected: Money,
        today: LocalDate,
    ): List<Insight> =
        if (today.dayOfMonth >= MIN_PACE_DAY && today.dayOfMonth < today.lengthOfMonth() && spent.paise > 0) {
            listOf(Insight.MonthEndForecast(spent, projected))
        } else {
            emptyList()
        }

    /** Totals of up to the last three full months that had any spending, newest first. */
    private fun pastMonthTotals(
        live: List<Expense>,
        month: YearMonth,
    ): List<Money> =
        (1..3)
            .map { month.minusMonths(it.toLong()) }
            .map { m -> live.filter { YearMonth.from(it.date) == m }.map { it.amount }.sum() }
            .filter { it.paise > 0 }

    private fun nextMonth(past: List<Money>): List<Insight> =
        if (past.size >= 2) listOf(Insight.NextMonthForecast(Money(past.map { it.paise }.sum() / past.size), past.size)) else emptyList()

    private fun emiLoad(
        emi: Money,
        past: List<Money>,
        projected: Money,
    ): List<Insight> {
        val average = if (past.isNotEmpty()) Money(past.map { it.paise }.sum() / past.size) else projected
        if (emi.paise <= 0 || average.paise <= 0) return emptyList()
        val p = (emi.paise * 100 / average.paise).toInt()
        return if (p >= EMI_NOTICE_PERCENT) listOf(Insight.EmiLoad(emi, average, p)) else emptyList()
    }
}
