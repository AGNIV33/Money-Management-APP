package com.agniv.hisaab.core.domain.repository

import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.Loan
import com.agniv.hisaab.core.domain.model.LoanPayment
import com.agniv.hisaab.core.domain.model.Member
import com.agniv.hisaab.core.domain.model.Session
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ExpenseRepository {
    /** Non-deleted expenses in [from, to] that [viewerUserId] may see, newest first. */
    fun observeExpenses(
        familyId: String,
        viewerUserId: String,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<Expense>>

    suspend fun getExpense(id: String): Expense?

    /** Saves on the phone and queues the change for sync. */
    suspend fun upsert(expense: Expense): Result<Unit>

    /** Soft delete (deleted = true) so other phones learn about it. */
    suspend fun delete(id: String): Result<Unit>
}

interface CategoryRepository {
    fun observeCategories(familyId: String): Flow<List<Category>>
}

interface MemberRepository {
    fun observeMembers(familyId: String): Flow<List<Member>>
}

interface SessionRepository {
    /** Null until the user has a family (signed-in or local). */
    val session: Flow<Session?>
}

/** A loan with its "paid" marks, as the list and detail screens need them together. */
data class LoanWithPayments(
    val loan: Loan,
    val payments: List<LoanPayment>,
)

interface LoanRepository {
    /** Non-deleted loans [viewerUserId] may see (PRIVATE only for the owner), with live payments. */
    fun observeLoans(
        familyId: String,
        viewerUserId: String,
    ): Flow<List<LoanWithPayments>>

    suspend fun getLoan(id: String): Loan?

    /** Saves on the phone and queues the change for sync. */
    suspend fun upsert(loan: Loan): Result<Unit>

    /** Soft delete. */
    suspend fun delete(id: String): Result<Unit>

    /** Marks (or with deleted = true, un-marks) one month's EMI as paid. */
    suspend fun savePayment(payment: LoanPayment): Result<Unit>
}
