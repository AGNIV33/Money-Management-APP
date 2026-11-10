package com.agniv.hisaab.core.domain.model

import com.agniv.hisaab.core.common.money.Money
import java.time.LocalDate

/** One category's total this month and last month, in paise. Only default category names ever appear here. */
data class AiCategoryTotal(
    val name: String,
    val thisPaise: Long,
    val lastPaise: Long,
)

/**
 * Everything sent for the AI saving tips (CLAUDE.md rule 6): totals only. No names, notes, shops,
 * phone numbers or account numbers can be in here, because there is no field that could hold them.
 */
data class AiReportRequest(
    val familyId: String,
    /** First day of the month the report is about. */
    val month: LocalDate,
    /** "en" or "hi". */
    val language: String,
    val totalPaise: Long,
    val previousTotalPaise: Long,
    val budgetPaise: Long?,
    val emiSharePercent: Int?,
    val categories: List<AiCategoryTotal>,
)

data class AiTip(
    val title: String,
    val detail: String,
    /** What following the tip might save each month; zero when the AI was not sure. */
    val estSaving: Money,
)

/** Why tips could not be made. The app falls back to its on-device insights in every case. */
enum class AiReportFailure { NOT_CONFIGURED, OFFLINE, NOT_ALLOWED, FAILED }

class AiReportException(
    val failure: AiReportFailure,
) : Exception(failure.name)
