package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.domain.loan.ReminderStage
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class UdhaarReminder(
    val item: UdhaarItem,
    val stage: ReminderStage,
) {
    /** Remembered after showing, so the same reminder never shows twice. Date second, like EMI keys, so old ones can be forgotten. */
    val key: String get() = "${item.udhaar.id}:${item.udhaar.dueDate}:$stage"
}

/**
 * Reminders for udhaar with a due date: 1 day before and on the day. Only for entries the viewer added
 * (so the whole family is not buzzed for one entry) and only while something is still left.
 * Past due is shown in red on the Udhaar screen instead of another notification.
 */
object UdhaarReminders {
    fun due(
        items: List<UdhaarItem>,
        viewerUserId: String,
        today: LocalDate,
    ): List<UdhaarReminder> =
        items
            .filter { it.udhaar.ownerUserId == viewerUserId && !it.settled }
            .mapNotNull { item ->
                val due = item.udhaar.dueDate ?: return@mapNotNull null
                when (ChronoUnit.DAYS.between(today, due)) {
                    0L -> UdhaarReminder(item, ReminderStage.TODAY)
                    1L -> UdhaarReminder(item, ReminderStage.ONE_DAY)
                    else -> null
                }
            }
}
