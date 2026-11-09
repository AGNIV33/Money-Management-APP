package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.model.Source
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.repository.ExpenseRepository
import com.agniv.hisaab.core.domain.util.IdGenerator
import com.agniv.hisaab.core.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

const val MAX_NOTE_LENGTH = 200

/** What the Add/Edit screen hands over. Amount is already parsed into paise. */
data class ExpenseDraft(
    val amount: Money,
    val categoryId: String,
    val date: LocalDate,
    val note: String? = null,
    val mode: PayMode = PayMode.UPI,
    val visibility: Visibility = Visibility.FAMILY,
    val source: Source = Source.MANUAL,
    /** Member who paid; null = the current user. */
    val paidBy: String? = null,
)

sealed class ExpenseError(
    message: String,
) : IllegalArgumentException(message) {
    data object AmountNotPositive : ExpenseError("Amount must be more than ₹0")

    data object NoCategory : ExpenseError("Pick a category")

    data object NoteTooLong : ExpenseError("Note is too long")

    data object NotFound : ExpenseError("Expense not found")
}

internal fun ExpenseDraft.validate(): ExpenseError? =
    when {
        amount.paise <= 0 -> ExpenseError.AmountNotPositive
        categoryId.isBlank() -> ExpenseError.NoCategory
        (note?.trim()?.length ?: 0) > MAX_NOTE_LENGTH -> ExpenseError.NoteTooLong
        else -> null
    }

private fun String?.cleanNote(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

class AddExpense
    @Inject
    constructor(
        private val repository: ExpenseRepository,
        private val ids: IdGenerator,
        private val time: TimeProvider,
    ) {
        suspend operator fun invoke(
            session: Session,
            draft: ExpenseDraft,
        ): Result<Expense> {
            draft.validate()?.let { return Result.failure(it) }
            val expense =
                Expense(
                    id = ids.newId(),
                    familyId = session.familyId,
                    paidBy = draft.paidBy ?: session.memberId,
                    ownerUserId = session.userId,
                    amount = draft.amount,
                    categoryId = draft.categoryId,
                    date = draft.date,
                    note = draft.note.cleanNote(),
                    mode = draft.mode,
                    visibility = draft.visibility,
                    source = draft.source,
                    recurringId = null,
                    updatedAt = time.now(),
                )
            return repository.upsert(expense).map { expense }
        }
    }

class UpdateExpense
    @Inject
    constructor(
        private val repository: ExpenseRepository,
        private val time: TimeProvider,
    ) {
        suspend operator fun invoke(
            id: String,
            draft: ExpenseDraft,
        ): Result<Expense> {
            draft.validate()?.let { return Result.failure(it) }
            val existing = repository.getExpense(id)
            return if (existing == null) {
                Result.failure(ExpenseError.NotFound)
            } else {
                val updated =
                    existing.copy(
                        paidBy = draft.paidBy ?: existing.paidBy,
                        amount = draft.amount,
                        categoryId = draft.categoryId,
                        date = draft.date,
                        note = draft.note.cleanNote(),
                        mode = draft.mode,
                        visibility = draft.visibility,
                        updatedAt = time.now(),
                    )
                repository.upsert(updated).map { updated }
            }
        }
    }

class DeleteExpense
    @Inject
    constructor(
        private val repository: ExpenseRepository,
    ) {
        suspend operator fun invoke(id: String): Result<Unit> = repository.delete(id)
    }

class ObserveMonthExpenses
    @Inject
    constructor(
        private val repository: ExpenseRepository,
    ) {
        operator fun invoke(
            session: Session,
            month: YearMonth,
        ): Flow<List<Expense>> = repository.observeExpenses(session.familyId, session.userId, month.atDay(1), month.atEndOfMonth())
    }

data class Share(
    val id: String,
    val total: Money,
)

data class MonthSummary(
    val month: YearMonth,
    val total: Money,
    val previousTotal: Money,
    /** Highest first. */
    val byCategory: List<Share>,
    /** Highest first. */
    val byMember: List<Share>,
    val count: Int,
) {
    /** Positive = spent more than last month. */
    val changeFromPrevious: Money get() = total - previousTotal
}

class ObserveMonthSummary
    @Inject
    constructor(
        private val observeMonthExpenses: ObserveMonthExpenses,
    ) {
        operator fun invoke(
            session: Session,
            month: YearMonth,
        ): Flow<MonthSummary> =
            combine(
                observeMonthExpenses(session, month),
                observeMonthExpenses(session, month.minusMonths(1)),
            ) { current, previous ->
                MonthSummary(
                    month = month,
                    total = current.map { it.amount }.sum(),
                    previousTotal = previous.map { it.amount }.sum(),
                    byCategory = current.sharesBy { it.categoryId },
                    byMember = current.sharesBy { it.paidBy },
                    count = current.size,
                )
            }

        private inline fun List<Expense>.sharesBy(key: (Expense) -> String): List<Share> =
            groupBy(key)
                .map { (id, items) -> Share(id, items.map { it.amount }.sum()) }
                .sortedByDescending { it.total }
    }
