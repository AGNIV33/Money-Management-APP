package com.agniv.hisaab.core.network

import com.agniv.hisaab.core.network.dto.UdhaarDto
import com.agniv.hisaab.core.network.dto.UdhaarPaymentDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Server side of udhaar sync. */
interface UdhaarRemote {
    suspend fun upsertEntries(rows: List<UdhaarDto>)

    suspend fun upsertPayments(rows: List<UdhaarPaymentDto>)

    /** Rows changed after [sinceIso] (server time), oldest first, including soft-deleted ones. */
    suspend fun entriesChangedSince(
        familyId: String,
        sinceIso: String?,
        limit: Int,
    ): List<UdhaarDto>

    suspend fun paymentsChangedSince(
        familyId: String,
        sinceIso: String?,
        limit: Int,
    ): List<UdhaarPaymentDto>
}

class SupabaseUdhaarRemote
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) : UdhaarRemote {
        // updated_at is null in pushed rows; the server trigger stamps it.
        override suspend fun upsertEntries(rows: List<UdhaarDto>) {
            supabase.from(ENTRIES).upsert(rows.map { it.copy(updatedAt = null) }) { onConflict = "id" }
        }

        override suspend fun upsertPayments(rows: List<UdhaarPaymentDto>) {
            supabase.from(PAYMENTS).upsert(rows.map { it.copy(updatedAt = null) }) { onConflict = "id" }
        }

        override suspend fun entriesChangedSince(
            familyId: String,
            sinceIso: String?,
            limit: Int,
        ): List<UdhaarDto> = changed(ENTRIES, familyId, sinceIso, limit).decodeList()

        override suspend fun paymentsChangedSince(
            familyId: String,
            sinceIso: String?,
            limit: Int,
        ): List<UdhaarPaymentDto> = changed(PAYMENTS, familyId, sinceIso, limit).decodeList()

        private suspend fun changed(
            table: String,
            familyId: String,
            sinceIso: String?,
            limit: Int,
        ) = supabase.from(table).select {
            filter {
                eq("family_id", familyId)
                if (sinceIso != null) gt("updated_at", sinceIso)
            }
            order("updated_at", Order.ASCENDING)
            limit(limit.toLong())
        }

        private companion object {
            const val ENTRIES = "udhaar"
            const val PAYMENTS = "udhaar_payments"
        }
    }
