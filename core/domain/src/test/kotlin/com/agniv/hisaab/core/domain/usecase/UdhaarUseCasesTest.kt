package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.model.Udhaar
import com.agniv.hisaab.core.domain.model.UdhaarDirection
import com.agniv.hisaab.core.domain.model.UdhaarPayment
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.repository.UdhaarRepository
import com.agniv.hisaab.core.domain.repository.UdhaarWithPayments
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

class UdhaarUseCasesTest {
    private val me = Session("u1", "f1", "m1")
    private val repo = FakeUdhaarRepo()
    private val time =
        object : TimeProvider {
            override fun now(): Instant = Instant.parse("2026-11-10T10:00:00Z")

            override fun today(): LocalDate = LocalDate.of(2026, 11, 10)
        }
    private var nextId = 0
    private val save = SaveUdhaar(repo, IdGenerator { "x${++nextId}" }, time)
    private val pay = AddUdhaarPayment(repo, IdGenerator { "p${++nextId}" }, time)
    private val observe = ObserveUdhaar(repo, time)

    private fun draft(
        direction: UdhaarDirection = UdhaarDirection.I_OWE,
        person: String = " Machhli wala ",
        rupees: Long = 500,
        due: LocalDate? = null,
    ) = UdhaarDraft(direction, person, Money.ofRupees(rupees), LocalDate.of(2026, 11, 9), due)

    private suspend fun item(id: String) = observe(me).first().items.first { it.udhaar.id == id }

    @Test
    fun `save trims, fills owner and keeps the link to an expense on edit`() =
        runTest {
            val first = save(me, draft().copy(expenseId = "e1")).getOrThrow()
            assertThat(first.person).isEqualTo("Machhli wala")
            assertThat(first.ownerUserId).isEqualTo("u1")
            val edited = save(Session("u2", "f1", "m2"), draft(rupees = 650), editId = first.id).getOrThrow()
            assertThat(edited.ownerUserId).isEqualTo("u1")
            assertThat(edited.expenseId).isEqualTo("e1")
            assertThat(edited.amount).isEqualTo(Money.ofRupees(650))
        }

    @Test
    fun `bad entries are refused`() =
        runTest {
            assertThat(save(me, draft(person = " ")).exceptionOrNull()).isEqualTo(UdhaarError.NoPerson)
            assertThat(save(me, draft(person = "x".repeat(41))).exceptionOrNull()).isEqualTo(UdhaarError.PersonTooLong)
            assertThat(save(me, draft(rupees = 0)).exceptionOrNull()).isEqualTo(UdhaarError.BadAmount)
            assertThat(save(me, draft(), editId = "nope").exceptionOrNull()).isEqualTo(UdhaarError.NotFound)
            assertThat(repo.rows).isEmpty()
        }

    @Test
    fun `part payments reduce what is left and settle at zero`() =
        runTest {
            val e = save(me, draft(rupees = 500)).getOrThrow()
            pay(me, item(e.id), Money.ofRupees(200)).getOrThrow()
            assertThat(item(e.id).remaining).isEqualTo(Money.ofRupees(300))
            assertThat(item(e.id).settled).isFalse()

            // More than is left, or nothing, is refused.
            assertThat(pay(me, item(e.id), Money.ofRupees(301)).exceptionOrNull()).isEqualTo(UdhaarError.BadPayment)
            assertThat(pay(me, item(e.id), Money.ZERO).exceptionOrNull()).isEqualTo(UdhaarError.BadPayment)

            pay(me, item(e.id), item(e.id).remaining).getOrThrow() // mark settled
            assertThat(item(e.id).settled).isTrue()
            assertThat(item(e.id).payments).hasSize(2)
        }

    @Test
    fun `a wrong payment can be taken back`() =
        runTest {
            val e = save(me, draft(rupees = 500)).getOrThrow()
            pay(me, item(e.id), Money.ofRupees(500)).getOrThrow()
            assertThat(item(e.id).settled).isTrue()
            RemoveUdhaarPayment(repo, time)(item(e.id).payments.single()).getOrThrow()
            assertThat(item(e.id).remaining).isEqualTo(Money.ofRupees(500))
        }

    @Test
    fun `totals split by direction, ignore settled, and overdue comes first`() =
        runTest {
            val gets = save(me, draft(UdhaarDirection.THEY_OWE_ME, "Ramesh", 1_000, due = LocalDate.of(2026, 11, 20))).getOrThrow()
            val late = save(me, draft(UdhaarDirection.THEY_OWE_ME, "Suresh", 2_000, due = LocalDate.of(2026, 11, 1))).getOrThrow()
            val gives = save(me, draft(UdhaarDirection.I_OWE, "Kirana", 700)).getOrThrow()
            val done = save(me, draft(UdhaarDirection.I_OWE, "Doodhwala", 300)).getOrThrow()
            pay(me, item(done.id), Money.ofRupees(300)).getOrThrow()
            pay(me, item(gets.id), Money.ofRupees(250)).getOrThrow()

            val overview = observe(me).first()
            assertThat(overview.youWillGet).isEqualTo(Money.ofRupees(2_750)) // 750 + 2000
            assertThat(overview.youWillGive).isEqualTo(Money.ofRupees(700))
            assertThat(overview.items.map { it.udhaar.id }).containsExactly(late.id, gets.id, gives.id, done.id).inOrder()
            assertThat(item(late.id).overdue).isTrue()
            assertThat(item(done.id).overdue).isFalse()
        }

    @Test
    fun `delete hides the entry`() =
        runTest {
            val e = save(me, draft()).getOrThrow()
            DeleteUdhaar(repo)(e.id).getOrThrow()
            assertThat(observe(me).first().items).isEmpty()
        }
}

private class FakeUdhaarRepo : UdhaarRepository {
    private val entries = MutableStateFlow<Map<String, Udhaar>>(emptyMap())
    private val payments = MutableStateFlow<Map<String, UdhaarPayment>>(emptyMap())
    val rows: Map<String, Udhaar> get() = entries.value

    override fun observe(
        familyId: String,
        viewerUserId: String,
    ): Flow<List<UdhaarWithPayments>> =
        combine(entries, payments) { es, ps ->
            es.values
                .filter { !it.deleted && (it.visibility == Visibility.FAMILY || it.ownerUserId == viewerUserId) }
                .map { e -> UdhaarWithPayments(e, ps.values.filter { it.udhaarId == e.id }) }
        }

    override suspend fun get(id: String): Udhaar? = entries.value[id]

    override suspend fun upsert(udhaar: Udhaar): Result<Unit> {
        entries.value = entries.value + (udhaar.id to udhaar)
        return Result.success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        val e = entries.value[id] ?: return Result.failure(UdhaarError.NotFound)
        return upsert(e.copy(deleted = true))
    }

    override suspend fun savePayment(payment: UdhaarPayment): Result<Unit> {
        payments.value = payments.value + (payment.id to payment)
        return Result.success(Unit)
    }
}
