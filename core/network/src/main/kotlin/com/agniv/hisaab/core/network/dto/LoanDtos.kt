package com.agniv.hisaab.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of public.loans. Dates are ISO (2026-11-10); updated_at is left out on push. */
@Serializable
data class LoanDto(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("owner_user") val ownerUser: String,
    val type: String,
    val lender: String,
    @SerialName("principal_paise") val principalPaise: Long,
    @SerialName("annual_rate_bps") val annualRateBps: Int,
    @SerialName("tenure_months") val tenureMonths: Int,
    @SerialName("emi_paise") val emiPaise: Long,
    @SerialName("first_emi_date") val firstEmiDate: String,
    @SerialName("due_day") val dueDay: Int,
    @SerialName("added_on") val addedOn: String,
    val visibility: String,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** Row of public.loan_payments. month is the first day of the month the EMI was due in. */
@Serializable
data class LoanPaymentDto(
    val id: String,
    @SerialName("loan_id") val loanId: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("owner_user") val ownerUser: String,
    val month: String,
    @SerialName("amount_paise") val amountPaise: Long,
    @SerialName("paid_on") val paidOn: String,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)
