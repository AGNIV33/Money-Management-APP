package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.domain.loan.EmiMath
import com.agniv.hisaab.core.domain.loan.LoanProgress
import com.agniv.hisaab.core.domain.loan.LoanTerms
import com.agniv.hisaab.core.domain.model.Loan
import com.agniv.hisaab.core.domain.model.LoanPayment
import com.agniv.hisaab.core.domain.model.LoanType
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.repository.LoanRepository
import com.agniv.hisaab.core.domain.util.IdGenerator
import com.agniv.hisaab.core.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

const val MAX_LENDER_LENGTH = 40

/** What the Add/Edit loan form hands over. [emi] null = use the formula's EMI. */
data class LoanDraft(
    val type: LoanType,
    val lender: String,
    val principal: Money,
    val annualRateBps: Int,
    val tenureMonths: Int,
    val emi: Money?,
    val firstEmiDate: LocalDate,
    val visibility: Visibility = Visibility.FAMILY,
)

sealed class LoanError(
    message: String,
) : IllegalArgumentException(message) {
    data object NoLender : LoanError("Enter the bank or lender")

    data object LenderTooLong : LoanError("Lender name is too long")

    data object BadPrincipal : LoanError("Loan amount must be ₹1 to ₹100 crore")

    data object BadRate : LoanError("Interest must be 0% to 42%")

    data object BadTenure : LoanError("Tenure must be 1 to 480 months")

    data object BadEmi : LoanError("EMI must be more than ₹0")

    /** The EMI does not even cover a month's interest, so the loan would never end. */
    data object EmiTooSmall : LoanError("EMI is too small for this loan")

    data object NotFound : LoanError("Loan not found")
}

internal fun LoanDraft.validate(): LoanError? {
    val minEmi = Money((principal.paise * annualRateBps + MONTHLY_BPS - 1) / MONTHLY_BPS)
    return when {
        lender.isBlank() -> LoanError.NoLender
        lender.trim().length > MAX_LENDER_LENGTH -> LoanError.LenderTooLong
        principal.paise !in Money.ofRupees(1).paise..LoanTerms.MAX_PRINCIPAL_PAISE -> LoanError.BadPrincipal
        annualRateBps !in 0..LoanTerms.MAX_RATE_BPS -> LoanError.BadRate
        tenureMonths !in 1..LoanTerms.MAX_TENURE_MONTHS -> LoanError.BadTenure
        emi != null && emi.paise <= 0 -> LoanError.BadEmi
        !EmiMath.isRepayable(LoanTerms(principal, annualRateBps, tenureMonths)) -> LoanError.EmiTooSmall
        emi != null && annualRateBps > 0 && emi <= minEmi -> LoanError.EmiTooSmall
        else -> null
    }
}

private const val MONTHLY_BPS = 120_000L

class SaveLoan
    @Inject
    constructor(
        private val repository: LoanRepository,
        private val ids: IdGenerator,
        private val time: TimeProvider,
    ) {
        /** [editId] null = new loan. Editing keeps owner, family and the date it was added. */
        suspend operator fun invoke(
            session: Session,
            draft: LoanDraft,
            editId: String? = null,
        ): Result<Loan> {
            val old = editId?.let { repository.getLoan(it) }
            val error = draft.validate() ?: LoanError.NotFound.takeIf { editId != null && old == null }
            if (error != null) return Result.failure(error)
            val emi = draft.emi ?: EmiMath.emi(draft.principal, draft.annualRateBps, draft.tenureMonths)
            val loan =
                Loan(
                    id = old?.id ?: ids.newId(),
                    familyId = old?.familyId ?: session.familyId,
                    ownerUserId = old?.ownerUserId ?: session.userId,
                    type = draft.type,
                    lender = draft.lender.trim(),
                    principal = draft.principal,
                    annualRateBps = draft.annualRateBps,
                    tenureMonths = draft.tenureMonths,
                    emi = emi,
                    firstEmiDate = draft.firstEmiDate,
                    dueDay = draft.firstEmiDate.dayOfMonth,
                    addedOn = old?.addedOn ?: time.today(),
                    visibility = draft.visibility,
                    updatedAt = time.now(),
                )
            return repository.upsert(loan).map { loan }
        }
    }

class DeleteLoan
    @Inject
    constructor(
        private val repository: LoanRepository,
    ) {
        suspend operator fun invoke(id: String): Result<Unit> = repository.delete(id)
    }

/**
 * Ticks (or un-ticks) the EMI due in [month]. The row id comes from loan + month, so two phones
 * marking the same EMI write the same row instead of two payments.
 */
class SetEmiPaid
    @Inject
    constructor(
        private val repository: LoanRepository,
        private val time: TimeProvider,
    ) {
        suspend operator fun invoke(
            session: Session,
            loan: Loan,
            month: YearMonth,
            paid: Boolean,
        ): Result<Unit> =
            repository.savePayment(
                LoanPayment(
                    id = paymentId(loan.id, month),
                    loanId = loan.id,
                    familyId = loan.familyId,
                    ownerUserId = session.userId,
                    month = month,
                    amount = loan.emi,
                    paidOn = time.today(),
                    updatedAt = time.now(),
                    deleted = !paid,
                ),
            )

        companion object {
            fun paymentId(
                loanId: String,
                month: YearMonth,
            ): String = UUID.nameUUIDFromBytes("$loanId:$month".toByteArray()).toString()
        }
    }

data class LoanItem(
    val loan: Loan,
    val progress: LoanProgress,
    val paidMonths: Set<YearMonth>,
)

data class LoansOverview(
    /** Open loans first, soonest EMI first; closed loans last. */
    val items: List<LoanItem>,
    /** Sum of EMIs of loans still running. */
    val monthlyEmi: Money,
    val outstanding: Money,
)

class ObserveLoans
    @Inject
    constructor(
        private val repository: LoanRepository,
        private val time: TimeProvider,
    ) {
        operator fun invoke(session: Session): Flow<LoansOverview> =
            repository.observeLoans(session.familyId, session.userId).map { rows ->
                val today = time.today()
                val items =
                    rows
                        .map { row ->
                            val months =
                                row.payments
                                    .filter { !it.deleted }
                                    .map { it.month }
                                    .toSet()
                            LoanItem(row.loan, LoanProgress.of(row.loan, months, today), months)
                        }.sortedWith(compareBy({ it.progress.isClosed }, { it.progress.next?.date }))
                val open = items.filter { !it.progress.isClosed }
                LoansOverview(
                    items = items,
                    monthlyEmi = open.map { it.loan.emi }.sum(),
                    outstanding = open.map { it.progress.outstanding }.sum(),
                )
            }
    }
