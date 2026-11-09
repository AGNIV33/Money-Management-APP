package com.agniv.hisaab.core.domain.usecase

import app.cash.turbine.test
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.repository.ExpenseRepository
import com.agniv.hisaab.core.domain.util.IdGenerator
import com.agniv.hisaab.core.domain.util.TimeProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class ExpenseUseCasesTest {
    private val me = Session(userId = "u1", familyId = "f1", memberId = "m1")
    private val sister = Session(userId = "u2", familyId = "f1", memberId = "m2")
    private val repo = FakeExpenseRepository()
    private val time = FixedTime(Instant.parse("2026-11-09T10:00:00Z"), LocalDate.of(2026, 11, 9))
    private var nextId = 0
    private val ids = IdGenerator { "e${++nextId}" }

    private val add = AddExpense(repo, ids, time)
    private val update = UpdateExpense(repo, time)
    private val delete = DeleteExpense(repo)
    private val restore = RestoreExpense(repo, time)
    private val observeMonth = ObserveMonthExpenses(repo)
    private val summary = ObserveMonthSummary(observeMonth)

    private fun draft(
        rupees: Long,
        category: String = "groceries",
        date: LocalDate = LocalDate.of(2026, 11, 5),
        note: String? = null,
        visibility: Visibility = Visibility.FAMILY,
    ) = ExpenseDraft(Money.ofRupees(rupees), category, date, note, visibility = visibility)

    @Test
    fun `add fills ids owner and timestamp`() =
        runTest {
            val saved = add(me, draft(250, note = "  doodh  ")).getOrThrow()
            assertThat(saved.id).isEqualTo("e1")
            assertThat(saved.paidBy).isEqualTo("m1")
            assertThat(saved.ownerUserId).isEqualTo("u1")
            assertThat(saved.note).isEqualTo("doodh")
            assertThat(saved.updatedAt).isEqualTo(time.now())
            assertThat(repo.getExpense("e1")).isEqualTo(saved)
        }

    @Test
    fun `add rejects zero or negative amount`() =
        runTest {
            assertThat(add(me, draft(0)).exceptionOrNull()).isEqualTo(ExpenseError.AmountNotPositive)
            assertThat(add(me, draft(-5)).exceptionOrNull()).isEqualTo(ExpenseError.AmountNotPositive)
            assertThat(repo.rows).isEmpty()
        }

    @Test
    fun `add rejects missing category and long note`() =
        runTest {
            assertThat(add(me, draft(10, category = " ")).exceptionOrNull()).isEqualTo(ExpenseError.NoCategory)
            assertThat(add(me, draft(10, note = "x".repeat(201))).exceptionOrNull()).isEqualTo(ExpenseError.NoteTooLong)
        }

    @Test
    fun `blank note becomes null`() =
        runTest {
            assertThat(add(me, draft(10, note = "   ")).getOrThrow().note).isNull()
        }

    @Test
    fun `update keeps owner and bumps timestamp`() =
        runTest {
            val saved = add(me, draft(100)).getOrThrow()
            time.instant = Instant.parse("2026-11-10T08:00:00Z")
            val updated = update(saved.id, draft(150, category = "milk")).getOrThrow()
            assertThat(updated.amount).isEqualTo(Money.ofRupees(150))
            assertThat(updated.categoryId).isEqualTo("milk")
            assertThat(updated.ownerUserId).isEqualTo("u1")
            assertThat(updated.updatedAt).isEqualTo(Instant.parse("2026-11-10T08:00:00Z"))
        }

    @Test
    fun `update missing expense fails`() =
        runTest {
            assertThat(update("nope", draft(10)).exceptionOrNull()).isEqualTo(ExpenseError.NotFound)
        }

    @Test
    fun `delete hides expense from month list`() =
        runTest {
            val saved = add(me, draft(100)).getOrThrow()
            delete(saved.id).getOrThrow()
            observeMonth(me, YearMonth.of(2026, 11)).test {
                assertThat(awaitItem()).isEmpty()
            }
        }

    @Test
    fun `restore brings a deleted expense back`() =
        runTest {
            val saved = add(me, draft(100)).getOrThrow()
            delete(saved.id).getOrThrow()
            restore(saved.id).getOrThrow()
            observeMonth(me, YearMonth.of(2026, 11)).test {
                assertThat(awaitItem().map { it.id }).containsExactly(saved.id)
            }
        }

    @Test
    fun `private expense is hidden from other members`() =
        runTest {
            add(me, draft(500, visibility = Visibility.PRIVATE)).getOrThrow()
            add(me, draft(200)).getOrThrow()
            observeMonth(sister, YearMonth.of(2026, 11)).test {
                assertThat(awaitItem().map { it.amount }).containsExactly(Money.ofRupees(200))
            }
            observeMonth(me, YearMonth.of(2026, 11)).test {
                assertThat(awaitItem()).hasSize(2)
            }
        }

    @Test
    fun `summary totals by category and member against last month`() =
        runTest {
            add(me, draft(300, category = "milk")).getOrThrow()
            add(me, draft(200, category = "groceries")).getOrThrow()
            add(sister, draft(1000, category = "groceries")).getOrThrow()
            add(me, draft(900, date = LocalDate.of(2026, 10, 20))).getOrThrow()

            summary(me, YearMonth.of(2026, 11)).test {
                val s = awaitItem()
                assertThat(s.total).isEqualTo(Money.ofRupees(1500))
                assertThat(s.previousTotal).isEqualTo(Money.ofRupees(900))
                assertThat(s.changeFromPrevious).isEqualTo(Money.ofRupees(600))
                assertThat(s.byCategory)
                    .containsExactly(
                        Share("groceries", Money.ofRupees(1200)),
                        Share("milk", Money.ofRupees(300)),
                    ).inOrder()
                assertThat(s.byMember)
                    .containsExactly(
                        Share("m2", Money.ofRupees(1000)),
                        Share("m1", Money.ofRupees(500)),
                    ).inOrder()
                assertThat(s.count).isEqualTo(3)
            }
        }

    @Test
    fun `month boundaries are inclusive`() =
        runTest {
            add(me, draft(1, date = LocalDate.of(2026, 11, 1))).getOrThrow()
            add(me, draft(2, date = LocalDate.of(2026, 11, 30))).getOrThrow()
            add(me, draft(4, date = LocalDate.of(2026, 12, 1))).getOrThrow()
            observeMonth(me, YearMonth.of(2026, 11)).test {
                assertThat(awaitItem()).hasSize(2)
            }
        }
}

private class FixedTime(
    var instant: Instant,
    var date: LocalDate,
) : TimeProvider {
    override fun now(): Instant = instant

    override fun today(): LocalDate = date
}

/** Applies the same visibility rule the Room query uses. */
private class FakeExpenseRepository : ExpenseRepository {
    private val state = MutableStateFlow<Map<String, Expense>>(emptyMap())
    val rows: Collection<Expense> get() = state.value.values

    override fun observeExpenses(
        familyId: String,
        viewerUserId: String,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<Expense>> =
        state.map { all ->
            all.values
                .filter { it.familyId == familyId && !it.deleted && it.date in from..to }
                .filter { it.visibility == Visibility.FAMILY || it.ownerUserId == viewerUserId }
                .sortedByDescending { it.date }
        }

    override suspend fun getExpense(id: String): Expense? = state.value[id]

    override suspend fun upsert(expense: Expense): Result<Unit> {
        state.value = state.value + (expense.id to expense)
        return Result.success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        val row = state.value[id] ?: return Result.failure(ExpenseError.NotFound)
        state.value = state.value + (id to row.copy(deleted = true))
        return Result.success(Unit)
    }
}
