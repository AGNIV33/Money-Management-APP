package com.agniv.hisaab.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of public.goals. Dates are ISO; updated_at is left out on push. */
@Serializable
data class GoalDto(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("owner_user") val ownerUser: String,
    val name: String,
    @SerialName("target_paise") val targetPaise: Long,
    val deadline: String? = null,
    val visibility: String,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class GoalContributionDto(
    val id: String,
    @SerialName("goal_id") val goalId: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("owner_user") val ownerUser: String,
    @SerialName("amount_paise") val amountPaise: Long,
    val date: String,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)
