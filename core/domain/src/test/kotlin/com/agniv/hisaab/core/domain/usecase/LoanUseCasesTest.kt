package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.loan.EmiMath
import com.agniv.hisaab.core.domain.model.Loan
import com.agniv.hisaab.core.domain.model.LoanPayment
import com.agniv.hisaab.core.domain.model.LoanType
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.repository.LoanRepository
import com.agniv.hisaab.core.domain.repository.LoanWithPayments
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
import java.time.YearMonth

class LoanUseCasesTest {
    private val me = Session(userId = "u1", familyId = "f1", memberId = "m1")
    private val repo = FakeLoanRepository()
    private val time =
        object : TimeProvider {
            override fun now(): Instant = Instant.parse("2026-11-10T10:00:00Z")

            override fun today(): LocalDate = LocalDate.of(2026, 11, 10)
        }
    private var nextId = 0
    private val save = SaveLoan(repo, IdGenerator { "l${++nextId}" }, time)
    private val setPaid = SetEmiPaid(repo, time)
    private val observe = ObserveLoans(repo, time)

    private fun draft(
        lender: String = " HDFC ",
        rupees: Long = 500_000,
        bps: Int = 1_050,
        months: Int = 60,
        emi: Money? = null,
        first: LocalDate = LocalDate.of(2026, 12, 5),
    ) = LoanDraft(LoanType.CAR, lender, Money.ofRupees(rupees), bps, months, emi, first)

    @Test
    fun `save fills formula emi, due day, owner and added date`() =
        runTest {
            val loan = save(me, draft()).getOrThrow()
            assertThat(loan.lender).isEqualTo("HDFC")
            assertThat(loan.emi).isEqualTo(Money.ofRupees(10_746, 95))
            assertThat(loan.dueDay).isEqualTo(5)
            assertThat(loan.ownerUserId).isEqualTo("u1")
            assertThat(loan.addedOn).isEqualTo(LocalDate.of(2026, 11, 10))
        }

    @Test
    fun `bank emi typed by the user is kept`() =
        runTest {
            assertThat(save(me, draft(emi = Money.ofRupees(10_750))).getOrThrow().emi).isEqualTo(Money.ofRupees(10_750))
        }

    @Test
    fun `editing keeps id, owner and added date`() =
        runTest {
            val first = save(me, draft()).getOrThrow()
            val other = Session("u2", "f1", "m2")
            val edited = save(other, draft(lender = "SBI"), editId = first.id).getOrThrow()
            assertThat(edited.id).isEqualTo(first.id)
            assertThat(edited.ownerUserId).isEqualTo("u1")
            assertThat(edited.addedOn).isEqualTo(first.addedOn)
            assertThat(repo.rows).hasSize(1)
        }

    @Test
    fun `invalid drafts are rejected`() =
        runTest {
            assertThat(save(me, draft(lender = " ")).exceptionOrNull()).isEqualTo(LoanError.NoLender)
            assertThat(save(me, draft(lender = "x".repeat(41))).exceptionOrNull()).isEqualTo(LoanError.LenderTooLong)
            assertThat(save(me, draft(rupees = 0)).exceptionOrNull()).isEqualTo(LoanError.BadPrincipal)
            assertThat(save(me, draft(bps = 4_201)).exceptionOrNull()).isEqualTo(LoanError.BadRate)
            assertThat(save(me, draft(months = 0)).exceptionOrNull()).isEqualTo(LoanError.BadTenure)
            assertThat(save(me, draft(emi = Money.ZERO)).exceptionOrNull()).isEqualTo(LoanError.BadEmi)
            // 10.5% on ₹5 lakh is ₹4,375 interest a month: an EMI of ₹4,000 never ends the loan.
            assertThat(save(me, draft(emi = Money.ofRupees(4_000))).exceptionOrNull()).isEqualTo(LoanError.EmiTooSmall)
            assertThat(save(me, draft(rupees = 100_000, bps = 4_200, months = 480)).exceptionOrNull())
                .isEqualTo(LoanError.EmiTooSmall)
            assertThat(repo.rows).isEmpty()
        }

    @Test
    fun `payment id is the same for the same loan and month`() {
        val a = SetEmiPaid.paymentId("l1", YearMonth.of(2026, 12))
        assertThat(SetEmiPaid.paymentId("l1", YearMonth.of(2026, 12))).isEqualTo(a)
        assertThat(SetEmiPaid.paymentId("l1", YearMonth.of(2027, 1))).isNotEqualTo(a)
    }

    @Test
    fun `overview totals open loans and marking paid moves next emi`() =
        runTest {
            val car = save(me, draft()).getOrThrow()
            save(me, draft(lender = "Bajaj", rupees = 30_000, bps = 0, months = 1, first = LocalDate.of(2026, 11, 1)))
            setPaid(me, repo.rows.getValue("l2"), YearMonth.of(2026, 11), paid = true)

            val overview = observe(me).first()
            // Bajaj is fully paid: listed last, not in the totals.
            assertThat(overview.items.map { it.loan.lender }).containsExactly("HDFC", "Bajaj").inOrder()
            assertThat(overview.monthlyEmi).isEqualTo(car.emi)
            assertThat(overview.outstanding).isEqualTo(car.principal)

            setPaid(me, car, YearMonth.of(2026, 12), paid = true)
            val after = observe(me).first().items.first()
            assertThat(after.progress.paidCount).isEqualTo(1)
            assertThat(after.progress.outstanding).isEqualTo(EmiMath.balanceAfter(car.terms, 1))

            setPaid(me, car, YearMonth.of(2026, 12), paid = false)
            assertThat(
                observe(me)
                    .first()
                    .items
                    .first()
                    .progress.paidCount,
            ).isEqualTo(0)
        }
}

private class FakeLoanRepository : LoanRepository {
    private val state = MutableStateFlow<Map<String, Loan>>(emptyMap())
    private val payments = MutableStateFlow<Map<String, LoanPayment>>(emptyMap())
    val rows: Map<String, Loan> get() = state.value

    override fun observeLoans(
        familyId: String,
        viewerUserId: String,
    ): Flow<List<LoanWithPayments>> =
        combine(state, payments) { loans, pays ->
            loans.values.filter { !it.deleted }.map { l -> LoanWithPayments(l, pays.values.filter { it.loanId == l.id }) }
        }

    override suspend fun getLoan(id: String): Loan? = state.value[id]

    override suspend fun upsert(loan: Loan): Result<Unit> {
        state.value = state.value + (loan.id to loan)
        return Result.success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        val loan = state.value[id] ?: return Result.failure(LoanError.NotFound)
        return upsert(loan.copy(deleted = true))
    }

    override suspend fun savePayment(payment: LoanPayment): Result<Unit> {
        payments.value = payments.value + (payment.id to payment)
        return Result.success(Unit)
    }
}
