package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.RecurRule
import com.agniv.hisaab.core.domain.model.Recurring
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.model.Source
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.repository.ExpenseRepository
import com.agniv.hisaab.core.domain.repository.RecurringRepository
import com.agniv.hisaab.core.domain.util.IdGenerator
import com.agniv.hisaab.core.domain.util.TimeProvider
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

/** Which dates a [Recurring] is due on. */
object RecurrenceSchedule {
    /** A phone that was off for over a year still catches up, but never floods thousands of rows at once. */
    const val MAX_CATCH_UP = 400
    private const val WEEK = 7L
    private const val LOOK_AHEAD_YEARS = 2L

    /** Dates after [Recurring.lastGenerated] up to and including [today], oldest first. */
    fun dueDates(
        r: Recurring,
        today: LocalDate,
    ): List<LocalDate> {
        val from = r.lastGenerated.plusDays(1)
        if (from > today) return emptyList()
        val all =
            when (r.rule) {
                RecurRule.DAILY -> generateSequence(from) { it.plusDays(1) }.takeWhile { it <= today }
                RecurRule.WEEKLY -> weekly(r.startDate, from, today)
                RecurRule.MONTHLY -> monthly(r.startDate, from, today)
            }
        return all.take(MAX_CATCH_UP).toList()
    }

    /** The next date after [Recurring.lastGenerated] this repeats on, for showing "next: 5 Dec". */
    fun nextDate(r: Recurring): LocalDate {
        val far = r.lastGenerated.plusYears(LOOK_AHEAD_YEARS)
        return dueDates(r, far).firstOrNull() ?: far
    }

    private fun weekly(
        start: LocalDate,
        from: LocalDate,
        to: LocalDate,
    ): Sequence<LocalDate> {
        val skip = ChronoUnit.DAYS.between(start, from).coerceAtLeast(0)
        val first = start.plusDays((skip + WEEK - 1) / WEEK * WEEK)
        return generateSequence(first) { it.plusWeeks(1) }.takeWhile { it <= to }
    }

    private fun monthly(
        start: LocalDate,
        from: LocalDate,
        to: LocalDate,
    ): Sequence<LocalDate> =
        generateSequence(0L) { it + 1 }
            .map { n ->
                val month = YearMonth.from(start).plusMonths(n)
                month.atDay(minOf(start.dayOfMonth, month.lengthOfMonth()))
            }.dropWhile { it < from }
            .takeWhile { it <= to }
}

/** What the "repeat" choice hands over; the amount etc. come from the expense being saved. */
data class RecurringDraft(
    val amount: Money,
    val categoryId: String,
    val mode: PayMode,
    val note: String?,
    val visibility: Visibility,
    val rule: RecurRule,
    /** The date of the expense just saved: it fixes the weekday / day of month, and is already made. */
    val startDate: LocalDate,
)

class SaveRecurring
    @Inject
    constructor(
        private val repository: RecurringRepository,
        private val ids: IdGenerator,
        private val time: TimeProvider,
    ) {
        suspend operator fun invoke(
            session: Session,
            draft: RecurringDraft,
        ): Result<Recurring> {
            if (draft.amount.paise <= 0) return Result.failure(ExpenseError.AmountNotPositive)
            val recurring =
                Recurring(
                    id = ids.newId(),
                    familyId = session.familyId,
                    ownerUserId = session.userId,
                    paidBy = session.memberId,
                    amount = draft.amount,
                    categoryId = draft.categoryId,
                    mode = draft.mode,
                    note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
                    visibility = draft.visibility,
                    rule = draft.rule,
                    startDate = draft.startDate,
                    lastGenerated = draft.startDate,
                    updatedAt = time.now(),
                )
            return repository.upsert(recurring).map { recurring }
        }
    }

class DeleteRecurring
    @Inject
    constructor(
        private val repository: RecurringRepository,
    ) {
        suspend operator fun invoke(id: String): Result<Unit> = repository.delete(id)
    }

/**
 * Makes the expenses that came due (RecurringWorker, daily + at app start). Safe to run twice:
 * expense ids come from recurring id + date, so a repeat run rewrites the same rows.
 * These are the user's own rules (set on purpose), not AI output, so they save without asking again.
 */
class GenerateRecurring
    @Inject
    constructor(
        private val recurring: RecurringRepository,
        private val expenses: ExpenseRepository,
        private val time: TimeProvider,
    ) {
        /** Returns how many expenses were made. */
        suspend operator fun invoke(session: Session): Int {
            val today = time.today()
            var made = 0
            for (r in recurring.getOwned(session.familyId, session.userId)) {
                val dates = RecurrenceSchedule.dueDates(r, today)
                if (dates.isEmpty()) continue
                val allSaved = dates.all { date -> expenses.upsert(expenseFor(r, date)).isSuccess }
                if (allSaved) {
                    made += dates.size
                    recurring.upsert(r.copy(lastGenerated = dates.last(), updatedAt = time.now()))
                }
            }
            return made
        }

        private fun expenseFor(
            r: Recurring,
            date: LocalDate,
        ) = Expense(
            id = expenseId(r.id, date),
            familyId = r.familyId,
            paidBy = r.paidBy,
            ownerUserId = r.ownerUserId,
            amount = r.amount,
            categoryId = r.categoryId,
            date = date,
            note = r.note,
            mode = r.mode,
            visibility = r.visibility,
            source = Source.RECURRING,
            recurringId = r.id,
            updatedAt = time.now(),
        )

        companion object {
            fun expenseId(
                recurringId: String,
                date: LocalDate,
            ): String = UUID.nameUUIDFromBytes("$recurringId:$date".toByteArray()).toString()
        }
    }
