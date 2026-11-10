package com.agniv.hisaab.core.domain.model

import com.agniv.hisaab.core.common.money.Money
import java.time.Instant
import java.time.LocalDate

enum class RecurRule { DAILY, WEEKLY, MONTHLY }

/**
 * An expense that repeats (rent, maid, milk, internet). [startDate] fixes the weekday / day of month;
 * [lastGenerated] is the newest date an expense was already made for, so each date is made once.
 * Only the owner's phone makes the expenses, and their ids come from this id + the date, so even if
 * two phones ran it the family would never see doubles.
 */
data class Recurring(
    val id: String,
    val familyId: String,
    val ownerUserId: String,
    val paidBy: String,
    val amount: Money,
    val categoryId: String,
    val mode: PayMode,
    val note: String?,
    val visibility: Visibility,
    val rule: RecurRule,
    val startDate: LocalDate,
    val lastGenerated: LocalDate,
    val updatedAt: Instant,
    val deleted: Boolean = false,
)
