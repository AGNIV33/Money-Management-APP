package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Goal
import com.agniv.hisaab.core.domain.model.GoalContribution
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.repository.GoalWithContributions
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class GoalRemindersTest {
    private val now = Instant.parse("2026-11-01T10:00:00Z")

    private fun goal(
        id: String = "g1",
        owner: String = "u1",
        deadline: LocalDate? = LocalDate.of(2027, 3, 15),
        target: Long = 40_000,
    ) = Goal(id, "f1", owner, "Fridge", Money.ofRupees(target), deadline, Visibility.FAMILY, now)

    private fun saving(
        goalId: String,
        rupees: Long,
        date: LocalDate,
    ) = GoalContribution("s-$goalId-$date-$rupees", goalId, "f1", "u1", Money.ofRupees(rupees), date, now)

    private fun due(
        today: LocalDate,
        vararg rows: GoalWithContributions,
        viewer: String = "u1",
    ) = GoalReminders.due(ObserveGoals.overview(rows.toList(), today).items, viewer, today)

    @Test
    fun `first week of the month nudges a goal with a date and nothing saved yet this month`() {
        val today = LocalDate.of(2026, 11, 3)
        val r = due(today, GoalWithContributions(goal(), emptyList())).single()
        assertThat(r.kind).isEqualTo(GoalReminderKind.MONTHLY)
        assertThat(r.month).isEqualTo(YearMonth.of(2026, 11))
        assertThat(r.key).isEqualTo("goal-g1:2026-11:MONTHLY")
        assertThat(r.item.perMonthNeeded).isNotNull()
    }

    @Test
    fun `no monthly nudge after the first week, once saved this month, or without a date`() {
        assertThat(due(LocalDate.of(2026, 11, 8), GoalWithContributions(goal(), emptyList()))).isEmpty()
        val today = LocalDate.of(2026, 11, 2)
        val savedThisMonth = GoalWithContributions(goal(), listOf(saving("g1", 100, today.minusDays(1))))
        assertThat(due(today, savedThisMonth)).isEmpty()
        // Saved last month only: still nudged this month.
        val savedLastMonth = GoalWithContributions(goal(), listOf(saving("g1", 100, LocalDate.of(2026, 10, 20))))
        assertThat(due(today, savedLastMonth)).hasSize(1)
        assertThat(due(today, GoalWithContributions(goal(deadline = null), emptyList()))).isEmpty()
    }

    @Test
    fun `a week before the date it says the date is near, and that wins over the monthly nudge`() {
        val today = LocalDate.of(2026, 12, 1)
        val r = due(today, GoalWithContributions(goal(deadline = LocalDate.of(2026, 12, 6)), emptyList())).single()
        assertThat(r.kind).isEqualTo(GoalReminderKind.DATE_NEAR)
        assertThat(r.daysLeft).isEqualTo(5)
        assertThat(r.key).isEqualTo("goal-g1:2026-12:DATE_NEAR")
        // On the day itself too; after the date, silence.
        assertThat(due(LocalDate.of(2026, 12, 6), GoalWithContributions(goal(deadline = LocalDate.of(2026, 12, 6)), emptyList())))
            .hasSize(1)
        assertThat(due(LocalDate.of(2026, 12, 7), GoalWithContributions(goal(deadline = LocalDate.of(2026, 12, 6)), emptyList())))
            .isEmpty()
    }

    @Test
    fun `reached goals and other members' goals stay quiet`() {
        val today = LocalDate.of(2026, 11, 2)
        val reached = GoalWithContributions(goal(target = 100), listOf(saving("g1", 100, LocalDate.of(2026, 10, 1))))
        assertThat(due(today, reached)).isEmpty()
        val theirs = GoalWithContributions(goal(owner = "u2"), emptyList())
        assertThat(due(today, theirs)).isEmpty()
    }
}
