package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.RecurRule
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.model.UdhaarDirection
import javax.inject.Inject

/** The optional extras of a new expense: "repeat" and "bought on credit". Each is made only if asked for. */
class AddExpenseExtras
    @Inject
    constructor(
        private val saveRecurring: SaveRecurring,
        private val saveUdhaar: SaveUdhaar,
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
        ) {
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
