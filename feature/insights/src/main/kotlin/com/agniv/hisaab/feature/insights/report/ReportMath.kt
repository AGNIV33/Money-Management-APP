package com.agniv.hisaab.feature.insights.report

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Expense
import java.time.YearMonth
import kotlin.math.roundToInt

/** One slice of the month's spending; [categoryId] null = "Other" (everything after the top few). */
data class Slice(
    val categoryId: String?,
    val total: Money,
    val percent: Int,
)

/** A plain, on-phone tip for next month (board: "2–3 short, plain sentences, not AI-generated"). */
sealed interface Tip {
    data class BigShare(
        val categoryId: String,
        val percent: Int,
    ) : Tip

    data class KeepUnder(
        val categoryId: String,
        val percent: Int,
    ) : Tip

    data class Saved(
        val percent: Int,
    ) : Tip

    data class LessThanLast(
        val percent: Int,
    ) : Tip

    data class MoreThanLast(
        val percent: Int,
    ) : Tip
}

/** Pure sums behind the Monthly Report and Insights boards, kept apart so they are easy to test. */
object ReportMath {
    const val TOP_SLICES = 5
    private const val PERCENT = 100.0
    private const val BIG_SHARE = 25
    private const val TIP_TARGET_STEP = 5
    private const val MAX_TIPS = 3

    fun inMonth(
        expenses: List<Expense>,
        month: YearMonth,
    ): List<Expense> = expenses.filter { !it.deleted && YearMonth.from(it.date) == month }

    fun total(expenses: List<Expense>): Money = Money(expenses.sumOf { it.amount.paise })

    /** Biggest categories first, the rest folded into one "Other" slice. Percents add up to about 100. */
    fun slices(
        expenses: List<Expense>,
        top: Int = TOP_SLICES,
    ): List<Slice> {
        val total = expenses.sumOf { it.amount.paise }
        if (total <= 0) return emptyList()
        val byCategory =
            expenses
                .groupBy { it.categoryId }
                .map { (id, list) -> id to list.sumOf { it.amount.paise } }
                .sortedByDescending { it.second }
        val head = byCategory.take(top).map { (id, paise) -> Slice(id, Money(paise), percentOf(paise, total)) }
        val rest = byCategory.drop(top).sumOf { it.second }
        return if (rest > 0) head + Slice(null, Money(rest), percentOf(rest, total)) else head
    }

    /** Whole percent of [part] in [whole]; null when there is no whole to compare with. */
    fun share(
        part: Money,
        whole: Money?,
    ): Int? = whole?.takeIf { it.paise > 0 }?.let { percentOf(part.paise, it.paise) }

    /** "12% less than last month": positive = spent more. Null when last month had nothing. */
    fun changeVsLast(
        thisMonth: Money,
        lastMonth: Money,
    ): Int? = lastMonth.paise.takeIf { it > 0 }?.let { ((thisMonth.paise - it) * PERCENT / it).roundToInt() }

    fun tips(
        slices: List<Slice>,
        savedPercent: Int?,
        change: Int?,
    ): List<Tip> {
        val tips = mutableListOf<Tip>()
        val biggest = slices.firstOrNull { it.categoryId != null }
        if (biggest != null && biggest.percent >= BIG_SHARE && biggest.categoryId != null) {
            tips += Tip.BigShare(biggest.categoryId, biggest.percent)
            // Aim a little lower next month, rounded to a friendly 5%.
            val target = ((biggest.percent - TIP_TARGET_STEP) / TIP_TARGET_STEP) * TIP_TARGET_STEP
            if (target > 0) tips += Tip.KeepUnder(biggest.categoryId, target)
        }
        when {
            savedPercent != null && savedPercent > 0 -> tips += Tip.Saved(savedPercent)
            change != null && change < 0 -> tips += Tip.LessThanLast(-change)
            change != null && change > 0 -> tips += Tip.MoreThanLast(change)
        }
        return tips.take(MAX_TIPS)
    }

    private fun percentOf(
        part: Long,
        whole: Long,
    ): Int = (part * PERCENT / whole).roundToInt()
}
