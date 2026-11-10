package com.agniv.hisaab.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.agniv.hisaab.core.database.entity.CategoryEntity
import com.agniv.hisaab.core.database.entity.ExpenseEntity
import com.agniv.hisaab.core.database.entity.FamilyEntity
import com.agniv.hisaab.core.database.entity.LoanEntity
import com.agniv.hisaab.core.database.entity.LoanPaymentEntity
import com.agniv.hisaab.core.database.entity.MemberEntity
import com.agniv.hisaab.core.database.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    /**
     * PRIVATE rows only for their owner (CLAUDE.md rule 7). The same rule lives in Supabase RLS;
     * this copy matters because pulled rows of other members could reach the phone in future.
     */
    @Query(
        """
        SELECT * FROM expenses
        WHERE family_id = :familyId
          AND deleted = 0
          AND date BETWEEN :fromEpochDay AND :toEpochDay
          AND (visibility = 'FAMILY' OR owner_user = :viewerUserId)
        ORDER BY date DESC, updated_at DESC
        """,
    )
    fun observeRange(
        familyId: String,
        viewerUserId: String,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: String): ExpenseEntity?

    @Upsert
    suspend fun upsert(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE family_id = :familyId")
    suspend fun allForFamily(familyId: String): List<ExpenseEntity>

    /** Moves phone-only expenses into a real family when the user signs in (see AccountLinker). */
    @Query(
        """
        UPDATE expenses SET
          family_id = :newFamilyId,
          owner_user = :newOwner,
          paid_by = CASE WHEN paid_by = :oldMemberId THEN :newMemberId ELSE paid_by END
        WHERE family_id = :oldFamilyId
        """,
    )
    suspend fun reassign(
        oldFamilyId: String,
        newFamilyId: String,
        newOwner: String,
        oldMemberId: String,
        newMemberId: String,
    )
}

@Dao
interface CategoryDao {
    @Query(
        """
        SELECT * FROM categories
        WHERE deleted = 0 AND (family_id IS NULL OR family_id = :familyId)
        ORDER BY sort_order, name
        """,
    )
    fun observeForFamily(familyId: String): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("SELECT COUNT(*) FROM categories WHERE is_default = 1")
    suspend fun defaultCount(): Int
}

@Dao
interface FamilyDao {
    @Query("SELECT * FROM families WHERE id = :id")
    fun observe(id: String): Flow<FamilyEntity?>

    @Upsert
    suspend fun upsert(family: FamilyEntity)

    @Query("SELECT * FROM families WHERE id = :id")
    suspend fun get(id: String): FamilyEntity?

    @Query("DELETE FROM families WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE family_id = :familyId AND deleted = 0 ORDER BY name")
    fun observeForFamily(familyId: String): Flow<List<MemberEntity>>

    @Upsert
    suspend fun upsert(member: MemberEntity)

    @Upsert
    suspend fun upsertAll(members: List<MemberEntity>)

    @Query("DELETE FROM members WHERE family_id = :familyId")
    suspend fun deleteForFamily(familyId: String)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY id LIMIT :limit")
    suspend fun peek(limit: Int): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun insert(item: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM sync_queue WHERE table_name = :table AND row_id = :rowId)")
    suspend fun hasPending(
        table: String,
        rowId: String,
    ): Boolean

    @Query("DELETE FROM sync_queue")
    suspend fun clear()

    @Query("UPDATE sync_queue SET attempts = attempts + 1 WHERE id = :id")
    suspend fun markAttempt(id: Long)
}

@Dao
interface LoanDao {
    /** PRIVATE loans only for their owner (CLAUDE.md rule 7), same as Supabase RLS. */
    @Query(
        """
        SELECT * FROM loans
        WHERE family_id = :familyId AND deleted = 0
          AND (visibility = 'FAMILY' OR owner_user = :viewerUserId)
        """,
    )
    fun observeVisible(
        familyId: String,
        viewerUserId: String,
    ): Flow<List<LoanEntity>>

    /** Payments of loans the viewer may see; a private loan's payments stay private too. */
    @Query(
        """
        SELECT p.* FROM loan_payments p
        JOIN loans l ON l.id = p.loan_id
        WHERE p.family_id = :familyId AND p.deleted = 0 AND l.deleted = 0
          AND (l.visibility = 'FAMILY' OR l.owner_user = :viewerUserId)
        """,
    )
    fun observeVisiblePayments(
        familyId: String,
        viewerUserId: String,
    ): Flow<List<LoanPaymentEntity>>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun get(id: String): LoanEntity?

    @Upsert
    suspend fun upsert(loan: LoanEntity)

    @Upsert
    suspend fun upsertPayment(payment: LoanPaymentEntity)

    @Query("SELECT * FROM loans WHERE family_id = :familyId")
    suspend fun allForFamily(familyId: String): List<LoanEntity>

    @Query("SELECT * FROM loan_payments WHERE family_id = :familyId")
    suspend fun allPaymentsForFamily(familyId: String): List<LoanPaymentEntity>

    /** Moves phone-only loans into a real family when the user signs in (see AccountLinker). */
    @Query("UPDATE loans SET family_id = :newFamilyId, owner_user = :newOwner WHERE family_id = :oldFamilyId")
    suspend fun reassign(
        oldFamilyId: String,
        newFamilyId: String,
        newOwner: String,
    )

    @Query("UPDATE loan_payments SET family_id = :newFamilyId, owner_user = :newOwner WHERE family_id = :oldFamilyId")
    suspend fun reassignPayments(
        oldFamilyId: String,
        newFamilyId: String,
        newOwner: String,
    )
}
