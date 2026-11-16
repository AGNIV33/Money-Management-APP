package com.agniv.hisaab.core.domain.usecase

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class GoalReminderKind {
    /** Early in the month: "put aside ₹X this month". */
    MONTHLY,

    /** The goal's date is a week or less away and it is not reached yet. */
    DATE_NEAR,
}

data class GoalReminder(
    val item: GoalItem,
    val kind: GoalReminderKind,
    /** The month the reminder belongs to, so each one shows once. */
    val month: YearMonth,
    /** DATE_NEAR only: days left until the goal's date (0 = today). */
    val daysLeft: Long = 0,
) {
    /** Same shape as the EMI keys ("id:month:kind") so the shared "already shown" set can prune it by month. */
    val key: String get() = "goal-${item.goal.id}:$month:$kind"
}

/**
 * Which goal reminders are due today. Only goals with a date can say how much is needed, so only those
 * remind. Only the goal's owner is reminded: a family goal would otherwise buzz every phone in the house.
 */
object GoalReminders {
    /** Monthly nudges only in the first week, so a goal made on the 20th is not nagged the same day. */
    const val MONTHLY_LAST_DAY = 7
    const val DATE_NEAR_DAYS = 7L

    fun due(
        items: List<GoalItem>,
        viewerUserId: String,
        today: LocalDate,
    ): List<GoalReminder> =
        items
            .filter { it.goal.ownerUserId == viewerUserId && !it.reached && !it.goal.deleted }
            .mapNotNull { item ->
                val deadline = item.goal.deadline ?: return@mapNotNull null
                val daysLeft = ChronoUnit.DAYS.between(today, deadline)
                val month = YearMonth.from(today)
                when {
                    daysLeft < 0 -> null
                    daysLeft <= DATE_NEAR_DAYS -> GoalReminder(item, GoalReminderKind.DATE_NEAR, YearMonth.from(deadline), daysLeft)
                    today.dayOfMonth <= MONTHLY_LAST_DAY &&
                        item.perMonthNeeded != null &&
                        item.contributions.none { YearMonth.from(it.date) == month } ->
                        GoalReminder(item, GoalReminderKind.MONTHLY, month)
                    else -> null
                }
            }
}
