package com.agniv.hisaab.core.network

import com.agniv.hisaab.core.network.dto.GoalContributionDto
import com.agniv.hisaab.core.network.dto.GoalDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Server side of goal sync. */
interface GoalRemote {
    suspend fun upsertGoals(rows: List<GoalDto>)

    suspend fun upsertContributions(rows: List<GoalContributionDto>)

    /** Rows changed after [sinceIso] (server time), oldest first, including soft-deleted ones. */
    suspend fun goalsChangedSince(
        familyId: String,
        sinceIso: String?,
        limit: Int,
    ): List<GoalDto>

    suspend fun contributionsChangedSince(
        familyId: String,
        sinceIso: String?,
        limit: Int,
    ): List<GoalContributionDto>
}

class SupabaseGoalRemote
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) : GoalRemote {
        // updated_at is null in pushed rows; the server trigger stamps it.
        override suspend fun upsertGoals(rows: List<GoalDto>) {
            supabase.from(GOALS).upsert(rows.map { it.copy(updatedAt = null) }) { onConflict = "id" }
        }

        override suspend fun upsertContributions(rows: List<GoalContributionDto>) {
            supabase.from(CONTRIBUTIONS).upsert(rows.map { it.copy(updatedAt = null) }) { onConflict = "id" }
        }

        override suspend fun goalsChangedSince(
            familyId: String,
            sinceIso: String?,
            limit: Int,
        ): List<GoalDto> = changed(GOALS, familyId, sinceIso, limit).decodeList()

        override suspend fun contributionsChangedSince(
            familyId: String,
            sinceIso: String?,
            limit: Int,
        ): List<GoalContributionDto> = changed(CONTRIBUTIONS, familyId, sinceIso, limit).decodeList()

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
            const val GOALS = "goals"
            const val CONTRIBUTIONS = "goal_contributions"
        }
    }
