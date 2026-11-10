package com.agniv.hisaab.core.domain.repository

import com.agniv.hisaab.core.domain.model.AiReportRequest
import com.agniv.hisaab.core.domain.model.AiTip
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.Goal
import com.agniv.hisaab.core.domain.model.GoalContribution
import com.agniv.hisaab.core.domain.model.Loan
import com.agniv.hisaab.core.domain.model.LoanPayment
import com.agniv.hisaab.core.domain.model.Member
import com.agniv.hisaab.core.domain.model.Recurring
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.model.Udhaar
import com.agniv.hisaab.core.domain.model.UdhaarPayment
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

interface RecurringRepository {
    /** Non-deleted repeating expenses [viewerUserId] may see (PRIVATE only for the owner). */
    fun observe(
        familyId: String,
        viewerUserId: String,
    ): Flow<List<Recurring>>

    /** The viewer's own, for making expenses: only the owner's phone does that. */
    suspend fun getOwned(
        familyId: String,
        userId: String,
    ): List<Recurring>

    suspend fun upsert(recurring: Recurring): Result<Unit>

    /** Soft delete: stops future expenses; the ones already made stay. */
    suspend fun delete(id: String): Result<Unit>
}

/** An udhaar entry with its payments (deleted ones included; the use case filters). */
data class UdhaarWithPayments(
    val udhaar: Udhaar,
    val payments: List<UdhaarPayment>,
)

interface UdhaarRepository {
    /** Non-deleted entries [viewerUserId] may see (PRIVATE only for the owner), with their payments. */
    fun observe(
        familyId: String,
        viewerUserId: String,
    ): Flow<List<UdhaarWithPayments>>

    suspend fun get(id: String): Udhaar?

    suspend fun upsert(udhaar: Udhaar): Result<Unit>

    /** Soft delete. */
    suspend fun delete(id: String): Result<Unit>

    /** Adds a payment, or with deleted = true takes one back. */
    suspend fun savePayment(payment: UdhaarPayment): Result<Unit>
}

interface AiReportRepository {
    /** The month's saving tips (made once per family per month on the server). Fails with an AiReportException. */
    suspend fun monthlyTips(request: AiReportRequest): Result<List<AiTip>>
}

/** A goal with its savings entries (deleted ones included; the use case filters). */
data class GoalWithContributions(
    val goal: Goal,
    val contributions: List<GoalContribution>,
)

interface GoalRepository {
    /** Non-deleted goals [viewerUserId] may see (PRIVATE only for the owner), with their entries. */
    fun observe(
        familyId: String,
        viewerUserId: String,
    ): Flow<List<GoalWithContributions>>

    suspend fun get(id: String): Goal?

    suspend fun upsert(goal: Goal): Result<Unit>

    /** Soft delete. */
    suspend fun delete(id: String): Result<Unit>

    /** Adds a savings entry, or with deleted = true takes one back. */
    suspend fun saveContribution(contribution: GoalContribution): Result<Unit>
}
