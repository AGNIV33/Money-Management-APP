package com.agniv.hisaab.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of public.udhaar. Dates are ISO; updated_at is left out on push. */
@Serializable
data class UdhaarDto(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("owner_user") val ownerUser: String,
    val direction: String,
    val person: String,
    @SerialName("amount_paise") val amountPaise: Long,
    val date: String,
    @SerialName("due_date") val dueDate: String? = null,
    val note: String? = null,
    @SerialName("expense_id") val expenseId: String? = null,
    val visibility: String,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class UdhaarPaymentDto(
    val id: String,
    @SerialName("udhaar_id") val udhaarId: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("owner_user") val ownerUser: String,
    @SerialName("amount_paise") val amountPaise: Long,
    @SerialName("paid_on") val paidOn: String,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)
