package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.common.money.Money

enum class BudgetLevel { OK, WARNING, OVER }

private const val WARNING_PERCENT = 80
private const val PERCENT = 100

/** 80% of the budget = warning, 100% = over (PRD F6, DESIGN.md colours). A zero budget never alerts. */
fun budgetLevel(
    spent: Money,
    budget: Money,
): BudgetLevel =
    when {
        budget.paise <= 0 -> BudgetLevel.OK
        // Integer maths on paise: no float rounding at the 80% edge.
        spent.paise >= budget.paise -> BudgetLevel.OVER
        spent.paise * PERCENT >= budget.paise * WARNING_PERCENT -> BudgetLevel.WARNING
        else -> BudgetLevel.OK
    }

/** 0.0 to 1.0+ share of the budget used, for progress bars. */
fun budgetFraction(
    spent: Money,
    budget: Money,
): Float = if (budget.paise <= 0) 0f else (spent.paise.toDouble() / budget.paise).toFloat()

/** One category's limit and what was spent in it this month. */
data class CategoryBudget(
    val categoryId: String,
    val spent: Money,
    val limit: Money,
) {
    val level: BudgetLevel get() = budgetLevel(spent, limit)
    val fraction: Float get() = budgetFraction(spent, limit)
}

/**
 * Progress for every category that has a limit, the most used first (closest to or over its limit on top).
 * [spentByCategory] is what the viewer can see, so PRIVATE items of others never count (CLAUDE.md rule 7).
 */
fun categoryBudgets(
    limits: Map<String, Money>,
    spentByCategory: Map<String, Money>,
): List<CategoryBudget> =
    limits
        .filter { it.value.paise > 0 }
        .map { (id, limit) -> CategoryBudget(id, spentByCategory[id] ?: Money.ZERO, limit) }
        .sortedWith(compareByDescending<CategoryBudget> { it.spent.paise * PERCENT / it.limit.paise }.thenBy { it.categoryId })
