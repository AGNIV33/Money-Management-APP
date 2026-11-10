package com.agniv.hisaab.core.data.report

import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.AiReportException
import com.agniv.hisaab.core.domain.model.AiReportFailure
import com.agniv.hisaab.core.domain.model.AiReportRequest
import com.agniv.hisaab.core.domain.model.AiTip
import com.agniv.hisaab.core.domain.repository.AiReportRepository
import com.agniv.hisaab.core.network.AiCategoryDto
import com.agniv.hisaab.core.network.AiReportRemote
import com.agniv.hisaab.core.network.AiReportReply
import com.agniv.hisaab.core.network.AiReportRequestDto
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Never saves anything itself: tips are shown to the user, who decides what to do (CLAUDE.md rule 4). */
class RemoteAiReportRepository
    @Inject
    constructor(
        private val remote: AiReportRemote,
        private val dispatchers: DispatcherProvider,
    ) : AiReportRepository {
        override suspend fun monthlyTips(request: AiReportRequest): Result<List<AiTip>> =
            withContext(dispatchers.io) {
                val reply =
                    try {
                        remote.fetch(request.toDto())
                    } catch (e: IOException) {
                        return@withContext Result.failure(AiReportException(AiReportFailure.OFFLINE).also { it.initCause(e) })
                    }
                when (reply) {
                    is AiReportReply.Tips ->
                        Result.success(
                            reply.tips.map { AiTip(it.title, it.detail, Money(it.estSavingPaise.coerceAtLeast(0))) },
                        )
                    is AiReportReply.Refused ->
                        Result.failure(
                            AiReportException(
                                when (reply.code) {
                                    "ai_not_configured" -> AiReportFailure.NOT_CONFIGURED
                                    "not_allowed", "not_signed_in" -> AiReportFailure.NOT_ALLOWED
                                    else -> AiReportFailure.FAILED
                                },
                            ),
                        )
                }
            }

        private fun AiReportRequest.toDto() =
            AiReportRequestDto(
                familyId = familyId,
                month = month.toString(),
                language = language,
                totalPaise = totalPaise,
                previousTotalPaise = previousTotalPaise,
                budgetPaise = budgetPaise,
                emiSharePercent = emiSharePercent,
                categories = categories.map { AiCategoryDto(it.name, it.thisPaise, it.lastPaise) },
            )
    }
