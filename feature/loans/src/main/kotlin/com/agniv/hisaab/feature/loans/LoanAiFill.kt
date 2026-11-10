package com.agniv.hisaab.feature.loans

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.loan.LoanExtract
import com.agniv.hisaab.core.domain.loan.LoanExtractFailure
import com.agniv.hisaab.core.domain.loan.LoanForm
import com.agniv.hisaab.core.domain.loan.LoanTerms
import kotlin.math.roundToInt

private const val BPS_PER_PERCENT = 100
private const val MONTHS_PER_YEAR = 12

/** Why "Fill with AI" did not fill anything. Each has its own plain sentence (en + hi). */
enum class LoanAiMessage { TOO_SHORT, OFFLINE, DAILY_LIMIT, NOTHING_FOUND, NOT_AVAILABLE, FAILED, PDF_LOCKED, READ_FAILED }

internal fun LoanExtractFailure.toMessage(): LoanAiMessage =
    when (this) {
        LoanExtractFailure.OFFLINE -> LoanAiMessage.OFFLINE
        LoanExtractFailure.DAILY_LIMIT -> LoanAiMessage.DAILY_LIMIT
        LoanExtractFailure.NOTHING_FOUND -> LoanAiMessage.NOTHING_FOUND
        LoanExtractFailure.NOT_CONFIGURED -> LoanAiMessage.NOT_AVAILABLE
        LoanExtractFailure.FAILED -> LoanAiMessage.FAILED
    }

/**
 * Puts what the AI found into the form. Only values that were clearly found replace what is there, and
 * anything outside the loan limits is ignored, so the form never ends up with a number it cannot save.
 * The person still checks everything and presses Save (CLAUDE.md rule 4).
 *
 * Each field is its own small "found it and it fits" check, so the branch count is high but flat.
 */
@Suppress("CyclomaticComplexMethod")
internal fun AddLoanUiState.withExtract(found: LoanExtract): AddLoanUiState {
    val principal = found.principalRupees?.takeIf { it * Money.PAISE_PER_RUPEE <= LoanTerms.MAX_PRINCIPAL_PAISE }
    val emi = found.emiRupees?.takeIf { it * Money.PAISE_PER_RUPEE <= LoanTerms.MAX_PRINCIPAL_PAISE }
    val bps = found.annualRatePercent?.let { (it * BPS_PER_PERCENT).roundToInt() }?.takeIf { it in 0..LoanTerms.MAX_RATE_BPS }
    val months = found.tenureMonths?.takeIf { it in 1..LoanTerms.MAX_TENURE_MONTHS }
    val inYears = months?.let { it % MONTHS_PER_YEAR == 0 } ?: tenureInYears
    return copy(
        type = found.type ?: type,
        lender = found.lender?.take(MAX_LENDER) ?: lender,
        amount = principal?.let { LoanForm.amountText(Money.ofRupees(it)) } ?: amount,
        rate = bps?.let(LoanForm::rateText) ?: rate,
        tenure = months?.let { (if (inYears) it / MONTHS_PER_YEAR else it).toString() } ?: tenure,
        tenureInYears = inYears,
        emi = emi?.let { LoanForm.amountText(Money.ofRupees(it)) } ?: this.emi,
        firstEmiDate = found.firstEmiDate ?: firstEmiDate,
        error = null,
    )
}

private const val MAX_LENDER = 40
