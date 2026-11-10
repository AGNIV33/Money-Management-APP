package com.agniv.hisaab.core.data.mapper

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.database.entity.UdhaarEntity
import com.agniv.hisaab.core.database.entity.UdhaarPaymentEntity
import com.agniv.hisaab.core.domain.model.Udhaar
import com.agniv.hisaab.core.domain.model.UdhaarDirection
import com.agniv.hisaab.core.domain.model.UdhaarPayment
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.network.dto.UdhaarDto
import com.agniv.hisaab.core.network.dto.UdhaarPaymentDto
import java.time.Instant
import java.time.LocalDate

fun UdhaarEntity.toDomain(): Udhaar =
    Udhaar(
        id = id,
        familyId = familyId,
        ownerUserId = ownerUser,
        direction = enumOr(direction, UdhaarDirection.I_OWE),
        person = person,
        amount = Money(amountPaise),
        date = LocalDate.ofEpochDay(date),
        dueDate = dueDate?.let(LocalDate::ofEpochDay),
        note = note,
        expenseId = expenseId,
        visibility = enumOr(visibility, Visibility.PRIVATE),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        deleted = deleted,
    )

fun Udhaar.toEntity(): UdhaarEntity =
    UdhaarEntity(
        id = id,
        familyId = familyId,
        ownerUser = ownerUserId,
        direction = direction.name,
        person = person,
        amountPaise = amount.paise,
        date = date.toEpochDay(),
        dueDate = dueDate?.toEpochDay(),
        note = note,
        expenseId = expenseId,
        visibility = visibility.name,
        updatedAt = updatedAt.toEpochMilli(),
        deleted = deleted,
    )

fun UdhaarEntity.toDto(): UdhaarDto =
    UdhaarDto(
        id = id,
        familyId = familyId,
        ownerUser = ownerUser,
        direction = direction,
        person = person,
        amountPaise = amountPaise,
        date = LocalDate.ofEpochDay(date).toString(),
        dueDate = dueDate?.let { LocalDate.ofEpochDay(it).toString() },
        note = note,
        expenseId = expenseId,
        visibility = visibility,
        deleted = deleted,
    )

fun UdhaarDto.toEntity(updatedAtMillis: Long): UdhaarEntity =
    UdhaarEntity(
        id = id,
        familyId = familyId,
        ownerUser = ownerUser,
        direction = direction,
        person = person,
        amountPaise = amountPaise,
        date = LocalDate.parse(date).toEpochDay(),
        dueDate = dueDate?.let { LocalDate.parse(it).toEpochDay() },
        note = note,
        expenseId = expenseId,
        visibility = visibility,
        updatedAt = updatedAtMillis,
        deleted = deleted,
    )

fun UdhaarPaymentEntity.toDomain(): UdhaarPayment =
    UdhaarPayment(
        id = id,
        udhaarId = udhaarId,
        familyId = familyId,
        ownerUserId = ownerUser,
        amount = Money(amountPaise),
        paidOn = LocalDate.ofEpochDay(paidOn),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        deleted = deleted,
    )

fun UdhaarPayment.toEntity(): UdhaarPaymentEntity =
    UdhaarPaymentEntity(
        id = id,
        udhaarId = udhaarId,
        familyId = familyId,
        ownerUser = ownerUserId,
        amountPaise = amount.paise,
        paidOn = paidOn.toEpochDay(),
        updatedAt = updatedAt.toEpochMilli(),
        deleted = deleted,
    )

fun UdhaarPaymentEntity.toDto(): UdhaarPaymentDto =
    UdhaarPaymentDto(
        id = id,
        udhaarId = udhaarId,
        familyId = familyId,
        ownerUser = ownerUser,
        amountPaise = amountPaise,
        paidOn = LocalDate.ofEpochDay(paidOn).toString(),
        deleted = deleted,
    )

fun UdhaarPaymentDto.toEntity(updatedAtMillis: Long): UdhaarPaymentEntity =
    UdhaarPaymentEntity(
        id = id,
        udhaarId = udhaarId,
        familyId = familyId,
        ownerUser = ownerUser,
        amountPaise = amountPaise,
        paidOn = LocalDate.parse(paidOn).toEpochDay(),
        updatedAt = updatedAtMillis,
        deleted = deleted,
    )
