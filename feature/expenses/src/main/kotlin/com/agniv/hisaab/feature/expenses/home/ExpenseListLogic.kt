package com.agniv.hisaab.feature.expenses.home

import androidx.compose.runtime.Immutable
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.PayMode
import java.time.LocalDate

@Immutable
data class ExpenseFilter(
    val query: String = "",
    val categoryId: String? = null,
    val mode: PayMode? = null,
) {
    val isActive: Boolean get() = query.isNotBlank() || categoryId != null || mode != null
}

@Immutable
data class DayGroup(
    val date: LocalDate,
    val total: Money,
    val expenses: List<Expense>,
)

/** Search matches the note or the category name (English or Hindi), ignoring case. */
internal fun List<Expense>.applyFilter(
    filter: ExpenseFilter,
    categories: Map<String, Category>,
): List<Expense> {
    val q = filter.query.trim()
    return filter { e ->
        val cat = categories[e.categoryId]
        (filter.categoryId == null || e.categoryId == filter.categoryId) &&
            (filter.mode == null || e.mode == filter.mode) &&
            (
                q.isEmpty() ||
                    e.note?.contains(q, ignoreCase = true) == true ||
                    cat?.name?.contains(q, ignoreCase = true) == true ||
                    cat?.nameHi?.contains(q, ignoreCase = true) == true
            )
    }
}

/** Newest day first, each with its day total (DESIGN.md §7.7). */
internal fun List<Expense>.groupByDay(): List<DayGroup> =
    groupBy { it.date }
        .toSortedMap(compareByDescending { it })
        .map { (date, items) -> DayGroup(date, items.map { it.amount }.sum(), items) }
