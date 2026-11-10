package com.agniv.hisaab.core.data.mapper

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.database.entity.RecurringEntity
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.RecurRule
import com.agniv.hisaab.core.domain.model.Recurring
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.network.dto.RecurringDto
import java.time.Instant
import java.time.LocalDate

fun RecurringEntity.toDomain(): Recurring =
    Recurring(
        id = id,
        familyId = familyId,
        ownerUserId = ownerUser,
        paidBy = paidBy,
        amount = Money(amountPaise),
        categoryId = categoryId,
        mode = enumOr(mode, PayMode.OTHER),
        note = note,
        visibility = enumOr(visibility, Visibility.PRIVATE),
        rule = enumOr(rule, RecurRule.MONTHLY),
        startDate = LocalDate.ofEpochDay(startDate),
        lastGenerated = LocalDate.ofEpochDay(lastGenerated),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        deleted = deleted,
    )

fun Recurring.toEntity(): RecurringEntity =
    RecurringEntity(
        id = id,
        familyId = familyId,
        ownerUser = ownerUserId,
        paidBy = paidBy,
        amountPaise = amount.paise,
        categoryId = categoryId,
        mode = mode.name,
        note = note,
        visibility = visibility.name,
        rule = rule.name,
        startDate = startDate.toEpochDay(),
        lastGenerated = lastGenerated.toEpochDay(),
        updatedAt = updatedAt.toEpochMilli(),
        deleted = deleted,
    )

fun RecurringEntity.toDto(): RecurringDto =
    RecurringDto(
        id = id,
        familyId = familyId,
        ownerUser = ownerUser,
        paidBy = paidBy,
        amountPaise = amountPaise,
        categoryId = categoryId,
        mode = mode,
        note = note,
        visibility = visibility,
        rule = rule,
        startDate = LocalDate.ofEpochDay(startDate).toString(),
        lastGenerated = LocalDate.ofEpochDay(lastGenerated).toString(),
        deleted = deleted,
    )

fun RecurringDto.toEntity(updatedAtMillis: Long): RecurringEntity =
    RecurringEntity(
        id = id,
        familyId = familyId,
        ownerUser = ownerUser,
        paidBy = paidBy,
        amountPaise = amountPaise,
        categoryId = categoryId,
        mode = mode,
        note = note,
        visibility = visibility,
        rule = rule,
        startDate = LocalDate.parse(startDate).toEpochDay(),
        lastGenerated = LocalDate.parse(lastGenerated).toEpochDay(),
        updatedAt = updatedAtMillis,
        deleted = deleted,
    )
