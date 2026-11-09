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
