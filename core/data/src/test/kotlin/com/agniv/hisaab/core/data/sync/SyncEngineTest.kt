package com.agniv.hisaab.core.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.data.repository.OfflineFirstExpenseRepository
import com.agniv.hisaab.core.data.repository.OfflineFirstLoanRepository
import com.agniv.hisaab.core.data.repository.OfflineFirstRecurringRepository
import com.agniv.hisaab.core.data.repository.OfflineFirstUdhaarRepository
import com.agniv.hisaab.core.database.HisaabDatabase
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.Loan
import com.agniv.hisaab.core.domain.model.LoanType
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.RecurRule
import com.agniv.hisaab.core.domain.model.Recurring
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.model.Source
import com.agniv.hisaab.core.domain.model.Udhaar
import com.agniv.hisaab.core.domain.model.UdhaarDirection
import com.agniv.hisaab.core.domain.model.UdhaarPayment
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.SetEmiPaid
import com.agniv.hisaab.core.domain.util.TimeProvider
import com.agniv.hisaab.core.network.ExpenseRemote
import com.agniv.hisaab.core.network.LoanRemote
import com.agniv.hisaab.core.network.RecurringRemote
import com.agniv.hisaab.core.network.UdhaarRemote
import com.agniv.hisaab.core.network.dto.ExpenseDto
import com.agniv.hisaab.core.network.dto.FamilyDto
import com.agniv.hisaab.core.network.dto.LoanDto
import com.agniv.hisaab.core.network.dto.LoanPaymentDto
import com.agniv.hisaab.core.network.dto.MemberDto
import com.agniv.hisaab.core.network.dto.RecurringDto
import com.agniv.hisaab.core.network.dto.UdhaarDto
import com.agniv.hisaab.core.network.dto.UdhaarPaymentDto
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncEngineTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HisaabDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
        }
    private val time =
        object : TimeProvider {
            override fun now(): Instant = Instant.parse("2026-11-09T10:00:00Z")

            override fun today(): LocalDate = LocalDate.of(2026, 11, 9)
        }
    private val me = Session("u1", "f1", "m1")
    private val sessions =
        object : SessionRepository {
            override val session: Flow<Session?> = flowOf(me)
        }
    private val server = FakeRemote(userId = "u1")
    private val loanServer = FakeLoanRemote()
    private val recurringServer = FakeRecurringRemote()
    private val udhaarServer = FakeUdhaarRemote()
    private val state = MemoryState()
    private val repo = OfflineFirstExpenseRepository(db, dispatchers, time) {}
    private val engine = SyncEngine(db, server, loanServer, recurringServer, udhaarServer, sessions, state, dispatchers)

    @After fun close() = db.close()

    private fun expense(
        id: String,
        rupees: Long = 100,
    ) = Expense(
        id = id,
        familyId = "f1",
        paidBy = "m1",
        ownerUserId = "u1",
        amount = Money.ofRupees(rupees),
        categoryId = "c1",
        date = LocalDate.of(2026, 11, 5),
        note = null,
        mode = PayMode.UPI,
        visibility = Visibility.FAMILY,
        source = Source.MANUAL,
        recurringId = null,
        updatedAt = time.now(),
    )

    private fun serverRow(
        id: String,
        rupees: Long,
        at: String,
        deleted: Boolean = false,
    ) = ExpenseDto(id, "f1", "m2", "u2", rupees * 100, "c1", "2026-11-06", null, "CASH", "FAMILY", "MANUAL", null, deleted, at)

    @Test
    fun `push uploads queued rows once and empties the queue`() =
        runTest {
            repo.upsert(expense("e1")).getOrThrow()
            repo.upsert(expense("e1", rupees = 150)).getOrThrow() // edited offline
            repo.upsert(expense("e2")).getOrThrow()

            engine.sync().getOrThrow()

            assertThat(server.pushed.map { it.id }).containsExactly("e1", "e2")
            assertThat(server.pushed.first { it.id == "e1" }.amountPaise).isEqualTo(15_000)
            assertThat(db.syncQueueDao().peek(10)).isEmpty()
        }

    @Test
    fun `failed push keeps the queue for the next try`() =
        runTest {
            repo.upsert(expense("e1")).getOrThrow()
            server.failPush = true

            assertThat(engine.sync().isFailure).isTrue()
            assertThat(
                db
                    .syncQueueDao()
                    .peek(10)
                    .single()
                    .attempts,
            ).isEqualTo(1)
        }

    @Test
    fun `pull saves other members' rows and remembers where it stopped`() =
        runTest {
            server.rows += serverRow("s1", 300, "2026-11-09T11:00:00.123456+00:00")
            server.rows += serverRow("s2", 50, "2026-11-09T11:05:00+00:00", deleted = true)

            engine.sync().getOrThrow()

            assertThat(db.expenseDao().get("s1")?.amountPaise).isEqualTo(30_000)
            assertThat(db.expenseDao().get("s2")?.deleted).isTrue()
            assertThat(state.lastPulled("f1")).isEqualTo("2026-11-09T11:05:00+00:00")
            assertThat(
                db
                    .memberDao()
                    .observeForFamily("f1")
                    .first()
                    .map { it.name },
            ).containsExactly("Didi")
        }

    @Test
    fun `pull does not overwrite a local edit that is still waiting to upload`() =
        runTest {
            repo.upsert(expense("e1", rupees = 999)).getOrThrow()
            server.failPush = true
            server.rows += serverRow("e1", 1, "2026-11-09T11:00:00+00:00")

            // Push fails, so the local edit stays queued; pull still runs and must leave it alone.
            assertThat(engine.sync().isFailure).isTrue()

            assertThat(db.expenseDao().get("e1")?.amountPaise).isEqualTo(99_900)
        }

    @Test
    fun `loans push before their payments and pull with their own clock`() =
        runTest {
            val loans = OfflineFirstLoanRepository(db, dispatchers, time) {}
            val loan =
                Loan(
                    "l1",
                    "f1",
                    "u1",
                    LoanType.CAR,
                    "SBI",
                    Money.ofRupees(100_000),
                    1_200,
                    12,
                    Money.ofRupees(8_885),
                    LocalDate.of(2026, 12, 5),
                    5,
                    LocalDate.of(2026, 11, 9),
                    Visibility.FAMILY,
                    time.now(),
                )
            loans.upsert(loan).getOrThrow()
            SetEmiPaid(loans, time)(me, loan, YearMonth.of(2026, 12), paid = true).getOrThrow()
            loans.upsert(loan.copy(lender = "SBI Car")).getOrThrow() // edited offline: one upload
            loanServer.rows +=
                LoanDto(
                    "l2",
                    "f1",
                    "u2",
                    "HOME",
                    "LIC",
                    500_000_000,
                    850,
                    240,
                    4_339_115,
                    "2026-12-01",
                    1,
                    "2026-11-09",
                    "FAMILY",
                    false,
                    "2026-11-09T12:00:00+00:00",
                )

            engine.sync().getOrThrow()

            assertThat(loanServer.calls).containsExactly("loans", "payments").inOrder()
            assertThat(loanServer.pushedLoans.single().lender).isEqualTo("SBI Car")
            assertThat(loanServer.pushedPayments.single().month).isEqualTo("2026-12-01")
            assertThat(db.loanDao().get("l2")?.lender).isEqualTo("LIC")
            assertThat(state.lastPulled("f1:loans")).isEqualTo("2026-11-09T12:00:00+00:00")
            assertThat(db.syncQueueDao().peek(10)).isEmpty()
        }

    @Test
    fun `private loans of others never show, nor their payments`() =
        runTest {
            val loans = OfflineFirstLoanRepository(db, dispatchers, time) {}
            val mine =
                Loan(
                    "l1",
                    "f1",
                    "u1",
                    LoanType.CAR,
                    "SBI",
                    Money.ofRupees(100_000),
                    1_200,
                    12,
                    Money.ofRupees(8_885),
                    LocalDate.of(2026, 12, 5),
                    5,
                    LocalDate.of(2026, 11, 9),
                    Visibility.PRIVATE,
                    time.now(),
                )
            loans.upsert(mine).getOrThrow()
            SetEmiPaid(loans, time)(me, mine, YearMonth.of(2026, 12), paid = true).getOrThrow()

            assertThat(
                loans
                    .observeLoans("f1", "u1")
                    .first()
                    .single()
                    .payments,
            ).hasSize(1)
            assertThat(loans.observeLoans("f1", "u2").first()).isEmpty()
            assertThat(db.loanDao().observeVisiblePayments("f1", "u2").first()).isEmpty()
        }

    @Test
    fun `repeating expenses sync both ways and private ones stay private`() =
        runTest {
            val recurring = OfflineFirstRecurringRepository(db, dispatchers, time) {}
            val mine =
                Recurring(
                    "r1",
                    "f1",
                    "u1",
                    "m1",
                    Money.ofRupees(12_000),
                    "c1",
                    PayMode.UPI,
                    "Rent",
                    Visibility.FAMILY,
                    RecurRule.MONTHLY,
                    LocalDate.of(2026, 11, 5),
                    LocalDate.of(2026, 11, 5),
                    time.now(),
                )
            recurring.upsert(mine).getOrThrow()
            recurring.upsert(mine.copy(amount = Money.ofRupees(13_000))).getOrThrow() // edited offline: one upload
            recurring.upsert(mine.copy(id = "r2", visibility = Visibility.PRIVATE)).getOrThrow()
            recurringServer.rows +=
                RecurringDto(
                    "r3",
                    "f1",
                    "u2",
                    "m2",
                    50_000,
                    "c1",
                    "CASH",
                    "Maid",
                    "FAMILY",
                    "DAILY",
                    "2026-11-01",
                    "2026-11-09",
                    false,
                    "2026-11-09T12:00:00+00:00",
                )

            engine.sync().getOrThrow()

            assertThat(recurringServer.pushed.map { it.id }).containsExactly("r1", "r2")
            assertThat(recurringServer.pushed.first { it.id == "r1" }.amountPaise).isEqualTo(1_300_000)
            assertThat(db.recurringDao().get("r3")?.note).isEqualTo("Maid")
            assertThat(db.syncQueueDao().peek(10)).isEmpty()
            // u2 sees the shared ones but not u1's private one.
            assertThat(recurring.observe("f1", "u2").first().map { it.id }).containsExactly("r1", "r3")
        }

    @Test
    fun `udhaar syncs both ways, entries before payments, private stays private`() =
        runTest {
            val repo = OfflineFirstUdhaarRepository(db, dispatchers, time) {}
            val entry =
                Udhaar(
                    "d1",
                    "f1",
                    "u1",
                    UdhaarDirection.I_OWE,
                    "Machhli wala",
                    Money.ofRupees(500),
                    LocalDate.of(2026, 11, 9),
                    null,
                    null,
                    null,
                    Visibility.FAMILY,
                    time.now(),
                )
            repo.upsert(entry).getOrThrow()
            repo.upsert(entry.copy(id = "d2", visibility = Visibility.PRIVATE)).getOrThrow()
            repo
                .savePayment(
                    UdhaarPayment("p1", "d1", "f1", "u1", Money.ofRupees(200), LocalDate.of(2026, 11, 10), time.now()),
                ).getOrThrow()
            udhaarServer.rows +=
                UdhaarDto(
                    "d3",
                    "f1",
                    "u2",
                    "THEY_OWE_ME",
                    "Ramesh",
                    100_000,
                    "2026-11-01",
                    "2026-11-20",
                    "lent",
                    null,
                    "FAMILY",
                    false,
                    "2026-11-09T12:00:00+00:00",
                )

            engine.sync().getOrThrow()

            assertThat(udhaarServer.calls).containsExactly("entries", "payments").inOrder()
            assertThat(udhaarServer.pushedEntries.map { it.id }).containsExactly("d1", "d2")
            assertThat(db.udhaarDao().get("d3")?.person).isEqualTo("Ramesh")
            assertThat(db.udhaarDao().get("d3")?.dueDate).isEqualTo(LocalDate.of(2026, 11, 20).toEpochDay())
            assertThat(db.syncQueueDao().peek(10)).isEmpty()
            assertThat(repo.observe("f1", "u2").first().map { it.udhaar.id }).containsExactly("d1", "d3")
            assertThat(
                repo
                    .observe("f1", "u2")
                    .first()
                    .first { it.udhaar.id == "d1" }
                    .payments,
            ).hasSize(1)
            assertThat(repo.observe("f1", "u1").first().map { it.udhaar.id }).containsExactly("d1", "d2", "d3")
        }

    @Test
    fun `nothing syncs while the family is still phone-only`() =
        runTest {
            val notLinked =
                SyncEngine(db, FakeRemote(userId = "someone-else"), loanServer, recurringServer, udhaarServer, sessions, state, dispatchers)
            repo.upsert(expense("e1")).getOrThrow()
            notLinked.sync().getOrThrow()
            assertThat(db.syncQueueDao().peek(10)).hasSize(1)
        }
}

private class MemoryState : SyncStateStore {
    private val map = mutableMapOf<String, String>()

    override suspend fun lastPulled(familyId: String): String? = map[familyId]

    override suspend fun setLastPulled(
        familyId: String,
        serverTime: String,
    ) {
        map[familyId] = serverTime
    }
}

private class FakeRemote(
    private val userId: String?,
) : ExpenseRemote {
    val pushed = mutableListOf<ExpenseDto>()
    val rows = mutableListOf<ExpenseDto>()
    var failPush = false

    override fun userId(): String? = userId

    override suspend fun upsert(rows: List<ExpenseDto>) {
        if (failPush) throw IOException("offline")
        pushed += rows
    }

    override suspend fun changedSince(
        familyId: String,
        sinceIso: String?,
        limit: Int,
    ): List<ExpenseDto> =
        rows
            .filter { it.familyId == familyId && (sinceIso == null || it.updatedAt.orEmpty() > sinceIso) }
            .sortedBy { it.updatedAt }
            .take(limit)

    var budget: Long? = null

    override suspend fun family(familyId: String): FamilyDto = FamilyDto(familyId, "Sarkar", "ABC234", budget)

    override suspend fun setMonthlyBudget(
        familyId: String,
        paise: Long?,
    ) {
        budget = paise
    }

    override suspend fun members(familyId: String): List<MemberDto> = listOf(MemberDto("m2", familyId, "u2", "Didi", "MEMBER"))
}

private class FakeLoanRemote : LoanRemote {
    val pushedLoans = mutableListOf<LoanDto>()
    val pushedPayments = mutableListOf<LoanPaymentDto>()
    val calls = mutableListOf<String>()
    val rows = mutableListOf<LoanDto>()

    override suspend fun upsertLoans(rows: List<LoanDto>) {
        calls += "loans"
        pushedLoans += rows
    }

    override suspend fun upsertPayments(rows: List<LoanPaymentDto>) {
        calls += "payments"
        pushedPayments += rows
    }

    override suspend fun loansChangedSince(
        familyId: String,
        sinceIso: String?,
        limit: Int,
    ): List<LoanDto> = rows.filter { sinceIso == null || it.updatedAt.orEmpty() > sinceIso }.take(limit)

    override suspend fun paymentsChangedSince(
        familyId: String,
        sinceIso: String?,
        limit: Int,
    ): List<LoanPaymentDto> = emptyList()
}

private class FakeRecurringRemote : RecurringRemote {
    val pushed = mutableListOf<RecurringDto>()
    val rows = mutableListOf<RecurringDto>()

    override suspend fun upsert(rows: List<RecurringDto>) {
        pushed += rows
    }

    override suspend fun changedSince(
        familyId: String,
        sinceIso: String?,
        limit: Int,
    ): List<RecurringDto> = rows.filter { sinceIso == null || it.updatedAt.orEmpty() > sinceIso }.take(limit)
}

private class FakeUdhaarRemote : UdhaarRemote {
    val calls = mutableListOf<String>()
    val pushedEntries = mutableListOf<UdhaarDto>()
    val pushedPayments = mutableListOf<UdhaarPaymentDto>()
    val rows = mutableListOf<UdhaarDto>()

    override suspend fun upsertEntries(rows: List<UdhaarDto>) {
        calls += "entries"
        pushedEntries += rows
    }

    override suspend fun upsertPayments(rows: List<UdhaarPaymentDto>) {
        calls += "payments"
        pushedPayments += rows
    }

    override suspend fun entriesChangedSince(
        familyId: String,
        sinceIso: String?,
        limit: Int,
    ): List<UdhaarDto> = rows.filter { sinceIso == null || it.updatedAt.orEmpty() > sinceIso }.take(limit)

    override suspend fun paymentsChangedSince(
        familyId: String,
        sinceIso: String?,
        limit: Int,
    ): List<UdhaarPaymentDto> = emptyList()
}
