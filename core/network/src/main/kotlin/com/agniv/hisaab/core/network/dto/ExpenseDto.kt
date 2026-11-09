package com.agniv.hisaab.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of public.expenses in Supabase. updated_at is left out on push: the server sets it. */
@Serializable
data class ExpenseDto(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("paid_by") val paidBy: String,
    @SerialName("owner_user") val ownerUser: String,
    @SerialName("amount_paise") val amountPaise: Long,
    @SerialName("category_id") val categoryId: String,
    /** ISO date, e.g. 2026-11-09. */
    val date: String,
    val note: String? = null,
    val mode: String,
    val visibility: String,
    val source: String,
    @SerialName("recurring_id") val recurringId: String? = null,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)
