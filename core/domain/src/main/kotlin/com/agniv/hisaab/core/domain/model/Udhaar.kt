package com.agniv.hisaab.core.domain.model

import com.agniv.hisaab.core.common.money.Money
import java.time.Instant
import java.time.LocalDate

/** Which way the money goes. */
enum class UdhaarDirection {
    /** Someone owes the family ("you will get"). */
    THEY_OWE_ME,

    /** The family owes someone, e.g. fish or groceries bought on credit ("you will give"). */
    I_OWE,
}

/**
 * One udhaar (credit) entry. [amount] is the full amount; part payments are separate [UdhaarPayment] rows,
 * so what is left is always amount minus payments, and no number is edited in place.
 * [expenseId] links an entry made by "bought on credit" in Add expense to that expense.
 */
data class Udhaar(
    val id: String,
    val familyId: String,
    val ownerUserId: String,
    val direction: UdhaarDirection,
    val person: String,
    val amount: Money,
    val date: LocalDate,
    val dueDate: LocalDate?,
    val note: String?,
    val expenseId: String?,
    val visibility: Visibility,
    val updatedAt: Instant,
    val deleted: Boolean = false,
)

data class UdhaarPayment(
    val id: String,
    val udhaarId: String,
    val familyId: String,
    val ownerUserId: String,
    val amount: Money,
    val paidOn: LocalDate,
    val updatedAt: Instant,
    val deleted: Boolean = false,
)
