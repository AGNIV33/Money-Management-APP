package com.agniv.hisaab.core.ml.report

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.domain.model.AiCategoryTotal
import com.agniv.hisaab.core.domain.model.AiReportRequest
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.model.Expense
import java.time.YearMonth

/**
 * Turns the family's expenses into the totals-only request for the AI tips (CLAUDE.md rule 6).
 * Anything that is not one of the built-in categories is counted as "Others", so a name the family typed
 * (a person or a shop used as a category) can never leave the phone.
 */
object ReportPayloadBuilder {
    private const val OTHERS = "Others"
    private const val PERCENT = 100L

    fun build(
        familyId: String,
        month: YearMonth,
        language: String,
        expenses: List<Expense>,
        categories: List<Category>,
        budget: Money?,
        monthlyEmi: Money,
    ): AiReportRequest {
        val nameOf = categories.associate { it.id to if (it.isDefault) it.name else OTHERS }
        val live = expenses.filter { !it.deleted }
        val now = live.filter { YearMonth.from(it.date) == month }
        val before = live.filter { YearMonth.from(it.date) == month.minusMonths(1) }
        val total = now.map { it.amount }.sum()

        fun byName(list: List<Expense>) =
            list.groupBy { nameOf[it.categoryId] ?: OTHERS }.mapValues { (_, v) ->
                v
                    .map { it.amount }
                    .sum()
                    .paise
            }
        val thisBy = byName(now)
        val lastBy = byName(before)
        val rows =
            (thisBy.keys + lastBy.keys)
                .map { AiCategoryTotal(it, thisBy[it] ?: 0L, lastBy[it] ?: 0L) }
                .sortedByDescending { it.thisPaise }
        return AiReportRequest(
            familyId = familyId,
            month = month.atDay(1),
            language = language,
            totalPaise = total.paise,
            previousTotalPaise = before.map { it.amount }.sum().paise,
            budgetPaise = budget?.paise,
            emiSharePercent = if (monthlyEmi.paise > 0 && total.paise > 0) (monthlyEmi.paise * PERCENT / total.paise).toInt() else null,
            categories = rows,
        )
    }
}
