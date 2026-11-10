package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.model.Udhaar
import com.agniv.hisaab.core.domain.model.UdhaarDirection
import com.agniv.hisaab.core.domain.model.UdhaarPayment
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.repository.UdhaarRepository
import com.agniv.hisaab.core.domain.repository.UdhaarWithPayments
import com.agniv.hisaab.core.domain.util.IdGenerator
import com.agniv.hisaab.core.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

const val MAX_PERSON_LENGTH = 40

sealed class UdhaarError(
    message: String,
) : IllegalArgumentException(message) {
    data object NoPerson : UdhaarError("Enter who it is with")

    data object PersonTooLong : UdhaarError("Name is too long")

    data object BadAmount : UdhaarError("Amount must be more than ₹0")

    data object NoteTooLong : UdhaarError("Note is too long")

    data object NotFound : UdhaarError("Entry not found")

    /** A payment of ₹0, or more than what is still left. */
    data object BadPayment : UdhaarError("Payment must be more than ₹0 and not more than what is left")
}

/** What the Add udhaar form (or "bought on credit") hands over. */
data class UdhaarDraft(
    val direction: UdhaarDirection,
    val person: String,
    val amount: Money,
    val date: LocalDate,
    val dueDate: LocalDate? = null,
    val note: String? = null,
    val expenseId: String? = null,
    val visibility: Visibility = Visibility.FAMILY,
)

internal fun UdhaarDraft.validate(): UdhaarError? =
    when {
        person.isBlank() -> UdhaarError.NoPerson
        person.trim().length > MAX_PERSON_LENGTH -> UdhaarError.PersonTooLong
        amount.paise <= 0 -> UdhaarError.BadAmount
        (note?.trim()?.length ?: 0) > MAX_NOTE_LENGTH -> UdhaarError.NoteTooLong
        else -> null
    }

class SaveUdhaar
    @Inject
    constructor(
        private val repository: UdhaarRepository,
        private val ids: IdGenerator,
        private val time: TimeProvider,
    ) {
        /** [editId] null = new. Editing keeps owner, family and any link to an expense. */
        suspend operator fun invoke(
            session: Session,
            draft: UdhaarDraft,
            editId: String? = null,
        ): Result<Udhaar> {
            val old = editId?.let { repository.get(it) }
            val error = draft.validate() ?: UdhaarError.NotFound.takeIf { editId != null && old == null }
            if (error != null) return Result.failure(error)
            val entry =
                Udhaar(
                    id = old?.id ?: ids.newId(),
                    familyId = old?.familyId ?: session.familyId,
                    ownerUserId = old?.ownerUserId ?: session.userId,
                    direction = draft.direction,
                    person = draft.person.trim(),
                    amount = draft.amount,
                    date = draft.date,
                    dueDate = draft.dueDate,
                    note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
                    expenseId = old?.expenseId ?: draft.expenseId,
                    visibility = draft.visibility,
                    updatedAt = time.now(),
                )
            return repository.upsert(entry).map { entry }
        }
    }

class DeleteUdhaar
    @Inject
    constructor(
        private val repository: UdhaarRepository,
    ) {
        suspend operator fun invoke(id: String): Result<Unit> = repository.delete(id)
    }

/** A part payment, or the whole rest ("mark settled") by passing [UdhaarItem.remaining]. */
class AddUdhaarPayment
    @Inject
    constructor(
        private val repository: UdhaarRepository,
        private val ids: IdGenerator,
        private val time: TimeProvider,
    ) {
        suspend operator fun invoke(
            session: Session,
            item: UdhaarItem,
            amount: Money,
        ): Result<Unit> {
            if (amount.paise <= 0 || amount > item.remaining) return Result.failure(UdhaarError.BadPayment)
            return repository.savePayment(
                UdhaarPayment(
                    id = ids.newId(),
                    udhaarId = item.udhaar.id,
                    familyId = item.udhaar.familyId,
                    ownerUserId = session.userId,
                    amount = amount,
                    paidOn = time.today(),
                    updatedAt = time.now(),
                ),
            )
        }
    }

/** Takes a wrong payment back (soft delete). */
class RemoveUdhaarPayment
    @Inject
    constructor(
        private val repository: UdhaarRepository,
        private val time: TimeProvider,
    ) {
        suspend operator fun invoke(payment: UdhaarPayment): Result<Unit> =
            repository.savePayment(payment.copy(deleted = true, updatedAt = time.now()))
    }

data class UdhaarItem(
    val udhaar: Udhaar,
    /** Live payments, oldest first. */
    val payments: List<UdhaarPayment>,
    val paid: Money,
    val remaining: Money,
    val overdue: Boolean,
) {
    val settled: Boolean get() = remaining.paise <= 0
}

data class UdhaarOverview(
    /** Open entries first (overdue, then soonest due, then newest); settled ones last. */
    val items: List<UdhaarItem>,
    /** Still to receive from others. */
    val youWillGet: Money,
    /** Still to pay to others. */
    val youWillGive: Money,
)

class ObserveUdhaar
    @Inject
    constructor(
        private val repository: UdhaarRepository,
        private val time: TimeProvider,
    ) {
        operator fun invoke(session: Session): Flow<UdhaarOverview> =
            repository.observe(session.familyId, session.userId).map { rows -> overview(rows, time.today()) }

        companion object {
            fun overview(
                rows: List<UdhaarWithPayments>,
                today: LocalDate,
            ): UdhaarOverview {
                val items =
                    rows
                        .map { row ->
                            val live = row.payments.filter { !it.deleted }.sortedBy { it.paidOn }
                            val paid = live.map { it.amount }.sum()
                            val remaining = Money(maxOf(0L, (row.udhaar.amount - paid).paise))
                            val overdue = remaining.paise > 0 && row.udhaar.dueDate?.let { it < today } == true
                            UdhaarItem(row.udhaar, live, paid, remaining, overdue)
                        }.sortedWith(
                            compareBy<UdhaarItem>({ it.settled }, { !it.overdue }, { it.udhaar.dueDate ?: LocalDate.MAX })
                                .thenByDescending { it.udhaar.date },
                        )
                val open = items.filter { !it.settled }

                fun total(direction: UdhaarDirection) = open.filter { it.udhaar.direction == direction }.map { it.remaining }.sum()
                return UdhaarOverview(items, total(UdhaarDirection.THEY_OWE_ME), total(UdhaarDirection.I_OWE))
            }
        }
    }
