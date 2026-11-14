package com.agniv.hisaab.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FamilyDto(
    val id: String,
    val name: String,
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("monthly_budget_paise") val monthlyBudgetPaise: Long? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val deleted: Boolean = false,
)

@Serializable
data class MemberDto(
    val id: String,
    @SerialName("family_id") val familyId: String,
    // Null once that person deleted their account (migration 013): only "Former member" is left.
    @SerialName("user_id") val userId: String?,
    val name: String,
    val role: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    val deleted: Boolean = false,
)

@Serializable
internal data class CreateFamilyParams(
    @SerialName("p_family_id") val familyId: String,
    @SerialName("p_member_id") val memberId: String,
    @SerialName("p_name") val name: String,
    @SerialName("p_member_name") val memberName: String,
    @SerialName("p_budget_paise") val budgetPaise: Long? = null,
)

@Serializable
internal data class JoinFamilyParams(
    @SerialName("p_code") val code: String,
    @SerialName("p_member_id") val memberId: String,
    @SerialName("p_member_name") val memberName: String,
)
