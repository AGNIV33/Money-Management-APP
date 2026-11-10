package com.agniv.hisaab.core.network

import com.agniv.hisaab.core.network.dto.CreateFamilyParams
import com.agniv.hisaab.core.network.dto.FamilyDto
import com.agniv.hisaab.core.network.dto.JoinFamilyParams
import com.agniv.hisaab.core.network.dto.MemberDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/** Thin wrapper over the family tables and RPCs in supabase/migrations/001_init.sql. */
class FamilyApi
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

        /** The family this account already belongs to (e.g. after reinstalling the app), or null. */
        suspend fun myMembership(): MemberDto? {
            val uid = currentUserId() ?: return null
            return supabase
                .from("members")
                .select {
                    filter {
                        eq("user_id", uid)
                        eq("deleted", false)
                    }
                    limit(1)
                }.decodeList<MemberDto>()
                .firstOrNull()
        }

        suspend fun family(id: String): FamilyDto? =
            supabase
                .from("families")
                .select { filter { eq("id", id) } }
                .decodeList<FamilyDto>()
                .firstOrNull()

        suspend fun members(familyId: String): List<MemberDto> =
            supabase
                .from("members")
                .select { filter { eq("family_id", familyId) } }
                .decodeList()

        /**
         * Changes one member row. Allowed by RLS: your own name, or anything for an admin. Only the fields that are
         * not null are sent. The database refuses to remove or demote the last admin.
         */
        suspend fun updateMember(
            memberId: String,
            name: String? = null,
            role: String? = null,
            deleted: Boolean? = null,
        ) {
            val change =
                buildJsonObject {
                    name?.let { put("name", it) }
                    role?.let { put("role", it) }
                    deleted?.let { put("deleted", it) }
                }
            supabase.from("members").update(change) { filter { eq("id", memberId) } }
        }

        suspend fun createFamily(
            familyId: String,
            memberId: String,
            name: String,
            memberName: String,
        ): FamilyDto =
            decode(
                supabase.postgrest
                    .rpc(
                        "create_family",
                        json.encodeToJsonElement(CreateFamilyParams.serializer(), CreateFamilyParams(familyId, memberId, name, memberName))
                            as JsonObject,
                    ).data,
            )

        suspend fun joinFamily(
            code: String,
            memberId: String,
            memberName: String,
        ): FamilyDto =
            decode(
                supabase.postgrest
                    .rpc(
                        "join_family",
                        json.encodeToJsonElement(JoinFamilyParams.serializer(), JoinFamilyParams(code, memberId, memberName)) as JsonObject,
                    ).data,
            )

        private fun decode(body: String): FamilyDto = json.decodeFromString(FamilyDto.serializer(), body)
    }
