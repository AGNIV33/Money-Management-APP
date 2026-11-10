package com.agniv.hisaab.core.domain.loan

import com.agniv.hisaab.core.domain.model.LoanType
import java.time.LocalDate

/**
 * What the AI found in a loan letter or description (IMPLEMENTATION_PLAN 4.8). Every field is optional: a value
 * that was not clearly stated is null. It only fills the form; the person checks it and presses Save.
 */
data class LoanExtract(
    val type: LoanType?,
    val lender: String?,
    val principalRupees: Long?,
    val annualRatePercent: Double?,
    val tenureMonths: Int?,
    val emiRupees: Long?,
    val firstEmiDate: LocalDate?,
)

enum class LoanExtractFailure { OFFLINE, DAILY_LIMIT, NOTHING_FOUND, NOT_CONFIGURED, FAILED }

class LoanExtractException(
    val failure: LoanExtractFailure,
) : Exception(failure.name)

interface LoanExtractRepository {
    /** [maskedText] must already have account, phone and ID numbers hidden (see PrivacyMask); the server checks again. */
    suspend fun extract(maskedText: String): Result<LoanExtract>
}
