package com.agniv.hisaab.core.domain.model

import com.agniv.hisaab.core.common.money.Money
import java.time.Instant
import java.time.LocalDate

/** A saving goal, e.g. "New fridge ₹40,000 by March". What is saved is the sum of [GoalContribution]s. */
data class Goal(
    val id: String,
    val familyId: String,
    val ownerUserId: String,
    val name: String,
    val target: Money,
    val deadline: LocalDate?,
    val visibility: Visibility,
    val updatedAt: Instant,
    val deleted: Boolean = false,
)

/** Money put towards a goal on [date]. Taking one back is a soft delete, so nothing is edited in place. */
data class GoalContribution(
    val id: String,
    val goalId: String,
    val familyId: String,
    val ownerUserId: String,
    val amount: Money,
    val date: LocalDate,
    val updatedAt: Instant,
    val deleted: Boolean = false,
)
