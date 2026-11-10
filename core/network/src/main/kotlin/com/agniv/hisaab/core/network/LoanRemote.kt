package com.agniv.hisaab.core.network

import com.agniv.hisaab.core.network.dto.LoanDto
import com.agniv.hisaab.core.network.dto.LoanPaymentDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Server side of loan sync. Separate from [ExpenseRemote] so each can be faked on its own in tests. */
interface LoanRemote {
    suspend fun upsertLoans(rows: List<LoanDto>)

    suspend fun upsertPayments(rows: List<LoanPaymentDto>)

    /** Rows changed after [sinceIso] (server time), oldest first, including soft-deleted ones. */
    suspend fun loansChangedSince(
        familyId: String,
        sinceIso: String?,
        limit: Int,
    ): List<LoanDto>

    suspend fun paymentsChangedSince(
        familyId: String,
        sinceIso: String?,
        limit: Int,
    ): List<LoanPaymentDto>
}

class SupabaseLoanRemote
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) : LoanRemote {
        // updated_at is null in pushed rows; the server trigger stamps it.
        override suspend fun upsertLoans(rows: List<LoanDto>) {
            supabase.from(LOANS).upsert(rows.map { it.copy(updatedAt = null) }) { onConflict = "id" }
        }

        override suspend fun upsertPayments(rows: List<LoanPaymentDto>) {
            supabase.from(PAYMENTS).upsert(rows.map { it.copy(updatedAt = null) }) { onConflict = "id" }
        }

        override suspend fun loansChangedSince(
            familyId: String,
            sinceIso: String?,
            limit: Int,
        ): List<LoanDto> = changed(LOANS, familyId, sinceIso, limit).decodeList()

        override suspend fun paymentsChangedSince(
            familyId: String,
            sinceIso: String?,
            limit: Int,
        ): List<LoanPaymentDto> = changed(PAYMENTS, familyId, sinceIso, limit).decodeList()

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
            const val LOANS = "loans"
            const val PAYMENTS = "loan_payments"
        }
    }
