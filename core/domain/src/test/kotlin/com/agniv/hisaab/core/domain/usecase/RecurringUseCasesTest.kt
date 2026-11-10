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
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RecurringUseCasesTest {
    private val me = Session("u1", "f1", "m1")
    private var today = LocalDate.of(2026, 12, 10)
    private val time =
        object : TimeProvider {
            override fun now(): Instant = Instant.parse("2026-12-10T10:00:00Z")

            override fun today(): LocalDate = today
        }

    private fun recurring(
        rule: RecurRule,
        start: LocalDate,
        last: LocalDate = start,
        owner: String = "u1",
    ) = Recurring(
        "r1",
        "f1",
        owner,
        "m1",
        Money.ofRupees(12_000),
        "rent",
        PayMode.UPI,
        "Rent",
        Visibility.FAMILY,
        rule,
        start,
        last,
        Instant.EPOCH,
    )

    @Test
    fun `monthly follows the day of month and clamps short months`() {
        val r = recurring(RecurRule.MONTHLY, LocalDate.of(2026, 1, 31))
        val dates = RecurrenceSchedule.dueDates(r, LocalDate.of(2026, 4, 30))
        assertThat(dates).containsExactly(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)).inOrder()
    }

    @Test
    fun `weekly keeps the weekday, daily is every day`() {
        val weekly = recurring(RecurRule.WEEKLY, LocalDate.of(2026, 12, 1)) // a Tuesday
        assertThat(RecurrenceSchedule.dueDates(weekly, LocalDate.of(2026, 12, 20)))
            .containsExactly(LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 15))
            .inOrder()
        val daily = recurring(RecurRule.DAILY, LocalDate.of(2026, 12, 7))
        assertThat(RecurrenceSchedule.dueDates(daily, LocalDate.of(2026, 12, 10))).hasSize(3)
    }

    @Test
    fun `nothing is due before the next date and the saved date is never repeated`() {
        val r = recurring(RecurRule.MONTHLY, LocalDate.of(2026, 12, 5))
        assertThat(RecurrenceSchedule.dueDates(r, LocalDate.of(2027, 1, 4))).isEmpty()
        assertThat(RecurrenceSchedule.dueDates(r, LocalDate.of(2027, 1, 5))).containsExactly(LocalDate.of(2027, 1, 5))
        assertThat(RecurrenceSchedule.nextDate(r)).isEqualTo(LocalDate.of(2027, 1, 5))
    }

    @Test
    fun `catch up is capped`() {
        val r = recurring(RecurRule.DAILY, LocalDate.of(2020, 1, 1))
        assertThat(RecurrenceSchedule.dueDates(r, LocalDate.of(2026, 12, 10))).hasSize(RecurrenceSchedule.MAX_CATCH_UP)
    }

    @Test
    fun `generator makes the due expenses once and moves the marker`() =
        runTest {
            val repo = FakeRecurringRepo(listOf(recurring(RecurRule.MONTHLY, LocalDate.of(2026, 9, 5))))
            val expenses = FakeExpenses()
            val generate = GenerateRecurring(repo, expenses, time)

            assertThat(generate(me)).isEqualTo(3) // Oct, Nov, Dec 5
            assertThat(expenses.saved.map { it.date })
                .containsExactly(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 5), LocalDate.of(2026, 12, 5))
            assertThat(expenses.saved.all { it.source == Source.RECURRING && it.recurringId == "r1" }).isTrue()
            assertThat(repo.items.single().lastGenerated).isEqualTo(LocalDate.of(2026, 12, 5))

            assertThat(generate(me)).isEqualTo(0)
            assertThat(expenses.saved).hasSize(3)

            today = LocalDate.of(2027, 1, 5)
            assertThat(generate(me)).isEqualTo(1)
        }

    @Test
    fun `only the owner's phone makes expenses and ids repeat for the same date`() =
        runTest {
            val repo = FakeRecurringRepo(listOf(recurring(RecurRule.DAILY, LocalDate.of(2026, 12, 8), owner = "u2")))
            val expenses = FakeExpenses()
            assertThat(GenerateRecurring(repo, expenses, time)(me)).isEqualTo(0)
            assertThat(GenerateRecurring.expenseId("r1", LocalDate.of(2026, 12, 9)))
                .isEqualTo(GenerateRecurring.expenseId("r1", LocalDate.of(2026, 12, 9)))
            assertThat(GenerateRecurring.expenseId("r1", LocalDate.of(2026, 12, 9)))
                .isNotEqualTo(GenerateRecurring.expenseId("r1", LocalDate.of(2026, 12, 10)))
        }

    @Test
    fun `saving a repeat starts from the saved expense's date`() =
        runTest {
            val repo = FakeRecurringRepo(emptyList())
            val save = SaveRecurring(repo, IdGenerator { "r9" }, time)
            val draft = RecurringDraft(Money.ofRupees(500), "milk", PayMode.CASH, " doodh ", Visibility.FAMILY, RecurRule.DAILY, today)
            val saved = save(me, draft).getOrThrow()
            assertThat(saved.lastGenerated).isEqualTo(today)
            assertThat(saved.note).isEqualTo("doodh")
            assertThat(save(me, draft.copy(amount = Money.ZERO)).isFailure).isTrue()
        }
}

private class FakeRecurringRepo(
    initial: List<Recurring>,
) : RecurringRepository {
    var items = initial

    override fun observe(
        familyId: String,
        viewerUserId: String,
    ): Flow<List<Recurring>> = flowOf(items)

    override suspend fun getOwned(
        familyId: String,
        userId: String,
    ) = items.filter { it.ownerUserId == userId && !it.deleted }

    override suspend fun upsert(recurring: Recurring): Result<Unit> {
        items = items.filter { it.id != recurring.id } + recurring
        return Result.success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit)
}

private class FakeExpenses : ExpenseRepository {
    val saved = mutableListOf<Expense>()

    override fun observeExpenses(
        familyId: String,
        viewerUserId: String,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<Expense>> = flowOf(saved)

    override suspend fun getExpense(id: String): Expense? = saved.firstOrNull { it.id == id }

    override suspend fun upsert(expense: Expense): Result<Unit> {
        saved.removeAll { it.id == expense.id }
        saved += expense
        return Result.success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit)
}
