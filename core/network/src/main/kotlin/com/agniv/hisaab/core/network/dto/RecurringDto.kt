package com.agniv.hisaab.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of public.recurring. Dates are ISO (2026-11-10); updated_at is left out on push. */
@Serializable
data class RecurringDto(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("owner_user") val ownerUser: String,
    @SerialName("paid_by") val paidBy: String,
    @SerialName("amount_paise") val amountPaise: Long,
    @SerialName("category_id") val categoryId: String,
    val mode: String,
    val note: String? = null,
    val visibility: String,
    val rule: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("last_generated") val lastGenerated: String,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)
