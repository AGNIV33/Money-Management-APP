package com.agniv.hisaab.core.network

import com.agniv.hisaab.core.network.dto.RecurringDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Server side of repeating-expense sync. */
interface RecurringRemote {
    suspend fun upsert(rows: List<RecurringDto>)

    /** Rows changed after [sinceIso] (server time), oldest first, including soft-deleted ones. */
    suspend fun changedSince(
        familyId: String,
        sinceIso: String?,
        limit: Int,
    ): List<RecurringDto>
}

class SupabaseRecurringRemote
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) : RecurringRemote {
        // updated_at is null in pushed rows; the server trigger stamps it.
        override suspend fun upsert(rows: List<RecurringDto>) {
            supabase.from(TABLE).upsert(rows.map { it.copy(updatedAt = null) }) { onConflict = "id" }
        }

        override suspend fun changedSince(
            familyId: String,
            sinceIso: String?,
            limit: Int,
        ): List<RecurringDto> =
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

        private companion object {
            const val TABLE = "recurring"
        }
    }
