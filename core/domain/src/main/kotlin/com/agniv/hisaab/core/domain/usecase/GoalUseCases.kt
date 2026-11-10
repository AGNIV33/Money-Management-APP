package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.domain.model.Goal
import com.agniv.hisaab.core.domain.model.GoalContribution
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.repository.GoalRepository
import com.agniv.hisaab.core.domain.repository.GoalWithContributions
import com.agniv.hisaab.core.domain.util.IdGenerator
import com.agniv.hisaab.core.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

const val MAX_GOAL_NAME_LENGTH = 40

sealed class GoalError(
    message: String,
) : IllegalArgumentException(message) {
    data object NoName : GoalError("Enter a name for the goal")

    data object NameTooLong : GoalError("Name is too long")

    data object BadTarget : GoalError("Target must be more than ₹0")

    data object DeadlinePassed : GoalError("Pick a date that has not passed")

    data object NotFound : GoalError("Goal not found")

    data object BadAmount : GoalError("Amount must be more than ₹0")
}

data class GoalDraft(
    val name: String,
    val target: Money,
    val deadline: LocalDate? = null,
    val visibility: Visibility = Visibility.FAMILY,
)

/** The first thing wrong with the form, or null. A goal may keep an old deadline when edited. */
private fun GoalDraft.validate(
    today: LocalDate,
    old: Goal?,
): GoalError? =
    when {
        name.isBlank() -> GoalError.NoName
        name.trim().length > MAX_GOAL_NAME_LENGTH -> GoalError.NameTooLong
        target.paise <= 0 -> GoalError.BadTarget
        deadline != null && deadline < today && deadline != old?.deadline -> GoalError.DeadlinePassed
        else -> null
    }

class SaveGoal
    @Inject
    constructor(
        private val repository: GoalRepository,
        private val ids: IdGenerator,
        private val time: TimeProvider,
    ) {
        /** [editId] null = new. A goal may keep an old deadline when edited, but a new one cannot be in the past. */
        suspend operator fun invoke(
            session: Session,
            draft: GoalDraft,
            editId: String? = null,
        ): Result<Goal> {
            val old = editId?.let { repository.get(it) }
            val error = draft.validate(time.today(), old) ?: GoalError.NotFound.takeIf { editId != null && old == null }
            if (error != null) return Result.failure(error)
            val goal =
                Goal(
                    id = old?.id ?: ids.newId(),
                    familyId = old?.familyId ?: session.familyId,
                    ownerUserId = old?.ownerUserId ?: session.userId,
                    name = draft.name.trim(),
                    target = draft.target,
                    deadline = draft.deadline,
                    visibility = draft.visibility,
                    updatedAt = time.now(),
                )
            return repository.upsert(goal).map { goal }
        }
    }

class DeleteGoal
    @Inject
    constructor(
        private val repository: GoalRepository,
    ) {
        suspend operator fun invoke(id: String): Result<Unit> = repository.delete(id)
    }

class AddGoalSavings
    @Inject
    constructor(
        private val repository: GoalRepository,
        private val ids: IdGenerator,
        private val time: TimeProvider,
    ) {
        suspend operator fun invoke(
            session: Session,
            goal: Goal,
            amount: Money,
        ): Result<Unit> {
            if (amount.paise <= 0) return Result.failure(GoalError.BadAmount)
            return repository.saveContribution(
                GoalContribution(ids.newId(), goal.id, goal.familyId, session.userId, amount, time.today(), time.now()),
            )
        }
    }

/** Takes a wrong entry back (soft delete). */
class RemoveGoalSavings
    @Inject
    constructor(
        private val repository: GoalRepository,
        private val time: TimeProvider,
    ) {
        suspend operator fun invoke(contribution: GoalContribution): Result<Unit> =
            repository.saveContribution(contribution.copy(deleted = true, updatedAt = time.now()))
    }

data class GoalItem(
    val goal: Goal,
    /** Live entries, oldest first. */
    val contributions: List<GoalContribution>,
    val saved: Money,
    val remaining: Money,
    /** 0 to 1, for the jar. */
    val fraction: Float,
    /** What to put aside each month to reach it by the deadline; null with no deadline or when done. */
    val perMonthNeeded: Money?,
    val monthsLeft: Int?,
) {
    val reached: Boolean get() = remaining.paise <= 0
}

data class GoalsOverview(
    /** Open goals first (soonest deadline first), reached ones last. */
    val items: List<GoalItem>,
    val totalSaved: Money,
    val totalTarget: Money,
)

class ObserveGoals
    @Inject
    constructor(
        private val repository: GoalRepository,
        private val time: TimeProvider,
    ) {
        operator fun invoke(session: Session): Flow<GoalsOverview> =
            repository.observe(session.familyId, session.userId).map { rows -> overview(rows, time.today()) }

        companion object {
            fun overview(
                rows: List<GoalWithContributions>,
                today: LocalDate,
            ): GoalsOverview {
                val items =
                    rows
                        .map { row ->
                            val live = row.contributions.filter { !it.deleted }.sortedBy { it.date }
                            val saved = live.map { it.amount }.sum()
                            val remaining = Money(maxOf(0L, (row.goal.target - saved).paise))
                            val fraction = (saved.paise.toDouble() / row.goal.target.paise).toFloat().coerceIn(0f, 1f)
                            val months = row.goal.deadline?.let { monthsUntil(today, it) }
                            val perMonth =
                                if (months != null &&
                                    remaining.paise > 0
                                ) {
                                    Money((remaining.paise + months - 1) / months)
                                } else {
                                    null
                                }
                            GoalItem(row.goal, live, saved, remaining, fraction, perMonth, months)
                        }.sortedWith(compareBy<GoalItem>({ it.reached }, { it.goal.deadline ?: LocalDate.MAX }).thenBy { it.goal.name })
                return GoalsOverview(items, items.map { it.saved }.sum(), items.map { it.goal.target }.sum())
            }

            /** Whole months from today to the deadline, at least 1 (a deadline this month means "this month"). */
            fun monthsUntil(
                today: LocalDate,
                deadline: LocalDate,
            ): Int = maxOf(1L, ChronoUnit.MONTHS.between(today.withDayOfMonth(1), deadline.withDayOfMonth(1))).toInt()
        }
    }
