package com.agniv.hisaab.core.data.loan

import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.domain.loan.LoanExtract
import com.agniv.hisaab.core.domain.loan.LoanExtractException
import com.agniv.hisaab.core.domain.loan.LoanExtractFailure
import com.agniv.hisaab.core.domain.loan.LoanExtractRepository
import com.agniv.hisaab.core.domain.model.LoanType
import com.agniv.hisaab.core.network.LoanExtractRemote
import com.agniv.hisaab.core.network.LoanExtractReply
import com.agniv.hisaab.core.network.LoanFieldsDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

/** Never saves anything: the result only fills the form, and the person confirms (CLAUDE.md rule 4). */
class RemoteLoanExtractRepository
    @Inject
    constructor(
        private val remote: LoanExtractRemote,
        private val dispatchers: DispatcherProvider,
    ) : LoanExtractRepository {
        override suspend fun extract(maskedText: String): Result<LoanExtract> =
            withContext(dispatchers.io) {
                try {
                    when (val reply = remote.extract(maskedText)) {
                        is LoanExtractReply.Found -> Result.success(reply.fields.toDomain())
                        is LoanExtractReply.Refused -> Result.failure(LoanExtractException(reply.code.toFailure()))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Result.failure(LoanExtractException(LoanExtractFailure.OFFLINE).also { it.initCause(e) })
                }
            }

        private fun String?.toFailure(): LoanExtractFailure =
            when (this) {
                "daily_limit" -> LoanExtractFailure.DAILY_LIMIT
                "nothing_found" -> LoanExtractFailure.NOTHING_FOUND
                "ai_not_configured" -> LoanExtractFailure.NOT_CONFIGURED
                else -> LoanExtractFailure.FAILED
            }

        private fun LoanFieldsDto.toDomain() =
            LoanExtract(
                type = loanType?.let { name -> LoanType.entries.firstOrNull { it.name == name } },
                lender = lender?.takeIf { it.isNotBlank() },
                principalRupees = principalRupees?.takeIf { it > 0 },
                annualRatePercent = annualRatePercent?.takeIf { it >= 0 },
                tenureMonths = tenureMonths?.takeIf { it > 0 },
                emiRupees = emiRupees?.takeIf { it > 0 },
                firstEmiDate = firstEmiDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            )
    }
