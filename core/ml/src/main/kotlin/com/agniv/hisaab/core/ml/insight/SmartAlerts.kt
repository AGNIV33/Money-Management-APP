package com.agniv.hisaab.core.ml.insight

import com.agniv.hisaab.core.common.money.Money
import java.time.LocalDate
import java.time.YearMonth

/**
 * An in-app insight worth a phone notification. [key] is remembered once shown so it never repeats;
 * its second part is always the month ("kind:yyyy-MM:..."), so old keys can be dropped by month.
 */
sealed interface SmartAlert {
    val key: String

    data class CategoryJump(
        val categoryId: String,
        val spent: Money,
        val percent: Int,
        override val key: String,
    ) : SmartAlert

    data class BigExpense(
        val categoryId: String,
        val amount: Money,
        val typical: Money,
        override val key: String,
    ) : SmartAlert

    /** The family budget will run out before the month ends at the current pace. Crossing it is BudgetAlertMonitor's job. */
    data class BudgetRunsOut(
        val day: Int,
        val safePerDay: Money,
        override val key: String,
    ) : SmartAlert

    /** Early in a month: last month's report card is ready to look at. */
    data class ReportReady(
        val month: YearMonth,
        override val key: String,
    ) : SmartAlert
}

/**
 * Picks which of the on-device insights should also become a system notification.
 * Only the "something needs a look" kinds: a jump, an unusual payment, a budget running out early.
 * Forecasts and repeat suggestions stay in the app; a notification for them would be noise.
 */
object SmartAlerts {
    /** The report card notification is only useful in the first days of a month. */
    const val REPORT_LAST_DAY = 3

    fun pick(
        insights: List<Insight>,
        today: LocalDate,
        lastMonthSpent: Money,
    ): List<SmartAlert> {
        val month = YearMonth.from(today)
        val out = mutableListOf<SmartAlert>()
        insights.forEach { insight ->
            when (insight) {
                is Insight.CategoryJump ->
                    out += SmartAlert.CategoryJump(insight.categoryId, insight.spent, insight.percent, "jump:$month:${insight.categoryId}")
                is Insight.BigExpense ->
                    out += SmartAlert.BigExpense(insight.categoryId, insight.amount, insight.typical, "big:$month:${insight.expenseId}")
                is Insight.BudgetPace -> {
                    val day = insight.crossesOnDay
                    if (!insight.alreadyOver && day != null) out += SmartAlert.BudgetRunsOut(day, insight.safePerDay, "pace:$month")
                }
                else -> Unit
            }
        }
        if (today.dayOfMonth <= REPORT_LAST_DAY && lastMonthSpent.paise > 0) {
            out += SmartAlert.ReportReady(month.minusMonths(1), "report:$month")
        }
        return out
    }
}
