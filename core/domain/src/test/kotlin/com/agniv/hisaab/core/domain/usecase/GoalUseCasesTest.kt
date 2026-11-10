package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Goal
import com.agniv.hisaab.core.domain.model.GoalContribution
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.repository.GoalRepository
import com.agniv.hisaab.core.domain.repository.GoalWithContributions
import com.agniv.hisaab.core.domain.util.IdGenerator
import com.agniv.hisaab.core.domain.util.TimeProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class GoalUseCasesTest {
    private val me = Session("u1", "f1", "m1")
    private val repo = FakeGoalRepo()
    private val today = LocalDate.of(2026, 11, 10)
    private val time =
        object : TimeProvider {
            override fun now(): Instant = Instant.parse("2026-11-10T10:00:00Z")

            override fun today(): LocalDate = today
        }
    private var nextId = 0
    private val save = SaveGoal(repo, IdGenerator { "g${++nextId}" }, time)
    private val add = AddGoalSavings(repo, IdGenerator { "s${++nextId}" }, time)
    private val observe = ObserveGoals(repo, time)

    private fun draft(
        name: String = " New fridge ",
        rupees: Long = 40_000,
        deadline: LocalDate? = LocalDate.of(2027, 3, 15),
    ) = GoalDraft(name, Money.ofRupees(rupees), deadline)

    private suspend fun item(id: String) = observe(me).first().items.first { it.goal.id == id }

    @Test
    fun `save trims and fills owner`() =
        runTest {
            val g = save(me, draft()).getOrThrow()
            assertThat(g.name).isEqualTo("New fridge")
            assertThat(g.ownerUserId).isEqualTo("u1")
        }

    @Test
    fun `bad goals are refused`() =
        runTest {
            assertThat(save(me, draft(name = " ")).exceptionOrNull()).isEqualTo(GoalError.NoName)
            assertThat(save(me, draft(name = "x".repeat(41))).exceptionOrNull()).isEqualTo(GoalError.NameTooLong)
            assertThat(save(me, draft(rupees = 0)).exceptionOrNull()).isEqualTo(GoalError.BadTarget)
            assertThat(save(me, draft(deadline = today.minusDays(1))).exceptionOrNull()).isEqualTo(GoalError.DeadlinePassed)
            assertThat(save(me, draft(), editId = "nope").exceptionOrNull()).isEqualTo(GoalError.NotFound)
            assertThat(repo.goals).isEmpty()
        }

    @Test
    fun `an old goal keeps its passed deadline when edited`() =
        runTest {
            val g = save(me, draft(deadline = today.plusDays(1))).getOrThrow()
            repo.upsert(g.copy(deadline = today.minusDays(5)))
            assertThat(save(me, draft(name = "Fridge", deadline = today.minusDays(5)), editId = g.id).isSuccess).isTrue()
        }

    @Test
    fun `savings add up, progress is capped and a mistake can be undone`() =
        runTest {
            val g = save(me, draft(rupees = 10_000)).getOrThrow()
            add(me, g, Money.ofRupees(2_500)).getOrThrow()
            add(me, g, Money.ofRupees(2_500)).getOrThrow()
            assertThat(item(g.id).saved).isEqualTo(Money.ofRupees(5_000))
            assertThat(item(g.id).fraction).isEqualTo(0.5f)
            assertThat(add(me, g, Money.ZERO).exceptionOrNull()).isEqualTo(GoalError.BadAmount)

            add(me, g, Money.ofRupees(8_000)).getOrThrow() // more than the target
            assertThat(item(g.id).fraction).isEqualTo(1f)
            assertThat(item(g.id).reached).isTrue()
            assertThat(item(g.id).remaining).isEqualTo(Money.ZERO)

            RemoveGoalSavings(repo, time)(item(g.id).contributions.last()).getOrThrow()
            assertThat(item(g.id).saved).isEqualTo(Money.ofRupees(5_000))
            assertThat(item(g.id).reached).isFalse()
        }

    @Test
    fun `monthly amount needed is spread over the months left`() =
        runTest {
            val g = save(me, draft(rupees = 40_000, deadline = LocalDate.of(2027, 3, 15))).getOrThrow()
            add(me, g, Money.ofRupees(10_000)).getOrThrow()
            // Nov 2026 -> Mar 2027 = 4 months; ₹30,000 left.
            assertThat(item(g.id).monthsLeft).isEqualTo(4)
            assertThat(item(g.id).perMonthNeeded).isEqualTo(Money.ofRupees(7_500))
            // A deadline this month still means one month, never a divide by zero.
            val soon = save(me, draft(name = "Gift", rupees = 1_000, deadline = today.plusDays(3))).getOrThrow()
            assertThat(item(soon.id).monthsLeft).isEqualTo(1)
            // No deadline: no monthly figure.
            val open = save(me, draft(name = "Someday", deadline = null)).getOrThrow()
            assertThat(item(open.id).perMonthNeeded).isNull()
        }

    @Test
    fun `open goals first by deadline, reached last, totals add up`() =
        runTest {
            val later = save(me, draft(name = "Later", rupees = 1_000, deadline = LocalDate.of(2027, 6, 1))).getOrThrow()
            val sooner = save(me, draft(name = "Sooner", rupees = 2_000, deadline = LocalDate.of(2027, 1, 1))).getOrThrow()
            val done = save(me, draft(name = "Done", rupees = 500, deadline = null)).getOrThrow()
            add(me, done, Money.ofRupees(500)).getOrThrow()
            add(me, later, Money.ofRupees(100)).getOrThrow()
            val o = observe(me).first()
            assertThat(o.items.map { it.goal.id }).containsExactly(sooner.id, later.id, done.id).inOrder()
            assertThat(o.totalSaved).isEqualTo(Money.ofRupees(600))
            assertThat(o.totalTarget).isEqualTo(Money.ofRupees(3_500))
        }

    @Test
    fun `delete hides the goal`() =
        runTest {
            val g = save(me, draft()).getOrThrow()
            DeleteGoal(repo)(g.id).getOrThrow()
            assertThat(observe(me).first().items).isEmpty()
        }
}

private class FakeGoalRepo : GoalRepository {
    private val goalsFlow = MutableStateFlow<Map<String, Goal>>(emptyMap())
    private val contributions = MutableStateFlow<Map<String, GoalContribution>>(emptyMap())
    val goals: Map<String, Goal> get() = goalsFlow.value

    override fun observe(
        familyId: String,
        viewerUserId: String,
    ): Flow<List<GoalWithContributions>> =
        combine(goalsFlow, contributions) { gs, cs ->
            gs.values
                .filter { !it.deleted && (it.visibility == Visibility.FAMILY || it.ownerUserId == viewerUserId) }
                .map { g -> GoalWithContributions(g, cs.values.filter { it.goalId == g.id }) }
        }

    override suspend fun get(id: String): Goal? = goalsFlow.value[id]

    override suspend fun upsert(goal: Goal): Result<Unit> {
        goalsFlow.value = goalsFlow.value + (goal.id to goal)
        return Result.success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        val g = goalsFlow.value[id] ?: return Result.failure(GoalError.NotFound)
        return upsert(g.copy(deleted = true))
    }

    override suspend fun saveContribution(contribution: GoalContribution): Result<Unit> {
        contributions.value = contributions.value + (contribution.id to contribution)
        return Result.success(Unit)
    }
}
