package com.agniv.hisaab.core.domain.loan

import com.agniv.hisaab.core.domain.model.Loan
import com.agniv.hisaab.core.domain.usecase.LoanItem
import java.time.LocalDate
import java.time.YearMonth

enum class ReminderStage { THREE_DAYS, ONE_DAY, TODAY }

data class EmiReminder(
    val loan: Loan,
    val month: YearMonth,
    val date: LocalDate,
    val stage: ReminderStage,
) {
    /** Remembered after showing, so the same reminder never shows twice. */
    val key: String get() = "${loan.id}:$month:$stage"
}

/**
 * Which EMI reminders are due on the day [LoanItem.progress] was computed for (PRD G3/F8: 3 days before, 1 day before, due day).
 * Only the next unpaid EMI of each loan counts, and only for the loan's owner: on a shared loan the
 * whole family would otherwise get buzzed for one payment.
 * A check that runs late (phone off for a day) still lands in the right stage: 2 days left counts as
 * the "3 days" reminder rather than being skipped.
 */
object EmiReminders {
    private const val THREE = 3L

    fun due(
        items: List<LoanItem>,
        viewerUserId: String,
    ): List<EmiReminder> =
        items
            .filter { it.loan.ownerUserId == viewerUserId }
            .mapNotNull { item ->
                val next = item.progress.next ?: return@mapNotNull null
                val stage =
                    when (next.daysLeft) {
                        0L -> ReminderStage.TODAY
                        1L -> ReminderStage.ONE_DAY
                        in 2L..THREE -> ReminderStage.THREE_DAYS
                        else -> null
                    } ?: return@mapNotNull null
                EmiReminder(item.loan, next.month, next.date, stage)
            }
}
