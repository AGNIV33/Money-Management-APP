package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.RecurRule
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.model.UdhaarDirection
import com.agniv.hisaab.core.domain.repository.GoalRepository
import javax.inject.Inject

/** The optional extras of a new expense: "repeat", "bought on credit" and "count towards a goal". Each is made only if asked for. */
class AddExpenseExtras
    @Inject
    constructor(
        private val saveRecurring: SaveRecurring,
        private val saveUdhaar: SaveUdhaar,
        private val goals: GoalRepository,
        private val addGoalSavings: AddGoalSavings,
    ) {
        /**
         * [expense] is the one just saved. A repeat carries on from its date; credit makes an "I owe"
         * entry with [creditFrom], linked to the expense, so Udhaar shows it under "you will give".
         */
        suspend operator fun invoke(
            session: Session,
            expense: Expense,
            repeat: RecurRule?,
            creditFrom: String?,
            goalId: String? = null,
        ) {
            // Money moved to savings (an RD, a piggy bank) is logged as an expense; this also counts it in the jar.
            // Dated like the expense so the goal's month matches. A goal deleted meanwhile is skipped quietly.
            goalId
                ?.let { goals.get(it) }
                ?.takeIf { !it.deleted }
                ?.let { addGoalSavings(session, it, expense.amount, expense.date) }
            if (creditFrom != null) {
                saveUdhaar(
                    session,
                    UdhaarDraft(
                        UdhaarDirection.I_OWE,
                        creditFrom,
                        expense.amount,
                        expense.date,
                        null,
                        expense.note,
                        expense.id,
                        expense.visibility,
                    ),
                )
            }
            if (repeat != null) {
                saveRecurring(
                    session,
                    RecurringDraft(
                        expense.amount,
                        expense.categoryId,
                        expense.mode,
                        expense.note,
                        expense.visibility,
                        repeat,
                        expense.date,
                    ),
                )
            }
        }
    }
