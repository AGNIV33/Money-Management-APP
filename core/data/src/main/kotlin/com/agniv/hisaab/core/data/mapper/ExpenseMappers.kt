package com.agniv.hisaab.core.data.mapper

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.database.entity.CategoryEntity
import com.agniv.hisaab.core.database.entity.ExpenseEntity
import com.agniv.hisaab.core.database.entity.MemberEntity
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.Member
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.Role
import com.agniv.hisaab.core.domain.model.Source
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.network.dto.ExpenseDto
import java.time.Instant
import java.time.LocalDate

fun ExpenseEntity.toDomain(): Expense =
    Expense(
        id = id,
        familyId = familyId,
        paidBy = paidBy,
        ownerUserId = ownerUser,
        amount = Money(amountPaise),
        categoryId = categoryId,
        date = LocalDate.ofEpochDay(date),
        note = note,
        mode = enumOr(mode, PayMode.OTHER),
        visibility = enumOr(visibility, Visibility.PRIVATE),
        source = enumOr(source, Source.MANUAL),
        recurringId = recurringId,
        updatedAt = Instant.ofEpochMilli(updatedAt),
        deleted = deleted,
    )

fun Expense.toEntity(): ExpenseEntity =
    ExpenseEntity(
        id = id,
        familyId = familyId,
        paidBy = paidBy,
        ownerUser = ownerUserId,
        amountPaise = amount.paise,
        categoryId = categoryId,
        date = date.toEpochDay(),
        note = note,
        mode = mode.name,
        visibility = visibility.name,
        source = source.name,
        recurringId = recurringId,
        updatedAt = updatedAt.toEpochMilli(),
        deleted = deleted,
    )

fun ExpenseEntity.toDto(): ExpenseDto =
    ExpenseDto(
        id = id,
        familyId = familyId,
        paidBy = paidBy,
        ownerUser = ownerUser,
        amountPaise = amountPaise,
        categoryId = categoryId,
        date = LocalDate.ofEpochDay(date).toString(),
        note = note,
        mode = mode,
        visibility = visibility,
        source = source,
        recurringId = recurringId,
        deleted = deleted,
    )

/** Server row -> local row. [updatedAtMillis] is the server's updated_at, the last-write-wins clock. */
fun ExpenseDto.toEntity(updatedAtMillis: Long): ExpenseEntity =
    ExpenseEntity(
        id = id,
        familyId = familyId,
        paidBy = paidBy,
        ownerUser = ownerUser,
        amountPaise = amountPaise,
        categoryId = categoryId,
        date = LocalDate.parse(date).toEpochDay(),
        note = note,
        mode = mode,
        visibility = visibility,
        source = source,
        recurringId = recurringId,
        updatedAt = updatedAtMillis,
        deleted = deleted,
    )

/** Postgres timestamptz text such as 2026-11-09T17:00:00.123456+00:00. */
fun parseServerTime(text: String): Long =
    java.time.OffsetDateTime
        .parse(text)
        .toInstant()
        .toEpochMilli()

fun CategoryEntity.toDomain(): Category =
    Category(
        id = id,
        familyId = familyId,
        name = name,
        nameHi = nameHi,
        icon = icon,
        color = color,
        isDefault = isDefault,
    )

fun MemberEntity.toDomain(): Member =
    Member(
        id = id,
        familyId = familyId,
        userId = userId,
        name = name,
        role = enumOr(role, Role.MEMBER),
    )

// An unknown value from a newer app version must not crash old phones.
// For visibility the fallback is PRIVATE: hiding too much is safer than leaking.
internal inline fun <reified E : Enum<E>> enumOr(
    value: String,
    fallback: E,
): E = enumValues<E>().firstOrNull { it.name == value } ?: fallback
