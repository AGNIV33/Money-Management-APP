package com.agniv.hisaab.core.network

import com.agniv.hisaab.core.network.dto.ExpenseDto
import com.agniv.hisaab.core.network.dto.MemberDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Server side of expense sync. An interface so the sync engine can be tested without a network. */
interface ExpenseRemote {
    /** Signed-in user id, or null when there is no login. */
    fun userId(): String?

    suspend fun upsert(rows: List<ExpenseDto>)

    /** Rows changed after [sinceIso] (server time), oldest first, including soft-deleted ones. */
    suspend fun changedSince(
        familyId: String,
        sinceIso: String?,
        limit: Int,
    ): List<ExpenseDto>

    /** Everyone in the family, so names show for expenses added by others. */
    suspend fun members(familyId: String): List<MemberDto>
}

class SupabaseExpenseRemote
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) : ExpenseRemote {
        override fun userId(): String? = supabase.auth.currentUserOrNull()?.id

        override suspend fun upsert(rows: List<ExpenseDto>) {
            // updated_at is null in pushed rows; the server trigger stamps it.
            supabase.from(TABLE).upsert(rows.map { it.copy(updatedAt = null) }) { onConflict = "id" }
        }

        override suspend fun changedSince(
            familyId: String,
            sinceIso: String?,
            limit: Int,
        ): List<ExpenseDto> =
            supabase
                .from(TABLE)
                .select {
                    filter {
                        eq("family_id", familyId)
                        if (sinceIso != null) gt("updated_at", sinceIso)
                    }
                    order("updated_at", Order.ASCENDING)
                    limit(limit.toLong())
                }.decodeList()

        override suspend fun members(familyId: String): List<MemberDto> =
            supabase.from("members").select { filter { eq("family_id", familyId) } }.decodeList()

        private companion object {
            const val TABLE = "expenses"
        }
    }
