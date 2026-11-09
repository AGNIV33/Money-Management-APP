package com.agniv.hisaab.core.domain.model

import com.agniv.hisaab.core.common.money.Money
import java.time.Instant
import java.time.LocalDate

enum class Role { ADMIN, MEMBER }

enum class PayMode { CASH, UPI, CARD, OTHER }

enum class Visibility { FAMILY, PRIVATE }

enum class Source { MANUAL, VOICE, SCAN, RECURRING }

data class Family(
    val id: String,
    val name: String,
    val inviteCode: String?,
    val monthlyBudget: Money?,
)

data class Member(
    val id: String,
    val familyId: String,
    val userId: String,
    val name: String,
    val role: Role,
)

/** familyId == null means a built-in default category. */
data class Category(
    val id: String,
    val familyId: String?,
    val name: String,
    val nameHi: String?,
    val icon: String,
    val color: Long,
    val isDefault: Boolean,
)

data class Expense(
    val id: String,
    val familyId: String,
    val paidBy: String,
    val ownerUserId: String,
    val amount: Money,
    val categoryId: String,
    val date: LocalDate,
    val note: String?,
    val mode: PayMode,
    val visibility: Visibility,
    val source: Source,
    val recurringId: String?,
    val updatedAt: Instant,
    val deleted: Boolean = false,
)

/** Who is using the app right now. Needed to filter PRIVATE rows (CLAUDE.md rule 7). */
data class Session(
    val userId: String,
    val familyId: String,
    val memberId: String,
)
