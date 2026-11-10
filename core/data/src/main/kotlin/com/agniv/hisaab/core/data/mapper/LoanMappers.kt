package com.agniv.hisaab.core.data.mapper

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.database.entity.LoanEntity
import com.agniv.hisaab.core.database.entity.LoanPaymentEntity
import com.agniv.hisaab.core.domain.model.Loan
import com.agniv.hisaab.core.domain.model.LoanPayment
import com.agniv.hisaab.core.domain.model.LoanType
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.network.dto.LoanDto
import com.agniv.hisaab.core.network.dto.LoanPaymentDto
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

fun LoanEntity.toDomain(): Loan =
    Loan(
        id = id,
        familyId = familyId,
        ownerUserId = ownerUser,
        type = enumOr(type, LoanType.OTHER),
        lender = lender,
        principal = Money(principalPaise),
        annualRateBps = annualRateBps,
        tenureMonths = tenureMonths,
        emi = Money(emiPaise),
        firstEmiDate = LocalDate.ofEpochDay(firstEmiDate),
        dueDay = dueDay,
        addedOn = LocalDate.ofEpochDay(addedOn),
        visibility = enumOr(visibility, Visibility.PRIVATE),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        deleted = deleted,
    )

fun Loan.toEntity(): LoanEntity =
    LoanEntity(
        id = id,
        familyId = familyId,
        ownerUser = ownerUserId,
        type = type.name,
        lender = lender,
        principalPaise = principal.paise,
        annualRateBps = annualRateBps,
        tenureMonths = tenureMonths,
        emiPaise = emi.paise,
        firstEmiDate = firstEmiDate.toEpochDay(),
        dueDay = dueDay,
        addedOn = addedOn.toEpochDay(),
        visibility = visibility.name,
        updatedAt = updatedAt.toEpochMilli(),
        deleted = deleted,
    )

fun LoanEntity.toDto(): LoanDto =
    LoanDto(
        id = id,
        familyId = familyId,
        ownerUser = ownerUser,
        type = type,
        lender = lender,
        principalPaise = principalPaise,
        annualRateBps = annualRateBps,
        tenureMonths = tenureMonths,
        emiPaise = emiPaise,
        firstEmiDate = LocalDate.ofEpochDay(firstEmiDate).toString(),
        dueDay = dueDay,
        addedOn = LocalDate.ofEpochDay(addedOn).toString(),
        visibility = visibility,
        deleted = deleted,
    )

fun LoanDto.toEntity(updatedAtMillis: Long): LoanEntity =
    LoanEntity(
        id = id,
        familyId = familyId,
        ownerUser = ownerUser,
        type = type,
        lender = lender,
        principalPaise = principalPaise,
        annualRateBps = annualRateBps,
        tenureMonths = tenureMonths,
        emiPaise = emiPaise,
        firstEmiDate = LocalDate.parse(firstEmiDate).toEpochDay(),
        dueDay = dueDay,
        addedOn = LocalDate.parse(addedOn).toEpochDay(),
        visibility = visibility,
        updatedAt = updatedAtMillis,
        deleted = deleted,
    )

fun LoanPaymentEntity.toDomain(): LoanPayment =
    LoanPayment(
        id = id,
        loanId = loanId,
        familyId = familyId,
        ownerUserId = ownerUser,
        month = YearMonth.from(LocalDate.ofEpochDay(month)),
        amount = Money(amountPaise),
        paidOn = LocalDate.ofEpochDay(paidOn),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        deleted = deleted,
    )

fun LoanPayment.toEntity(): LoanPaymentEntity =
    LoanPaymentEntity(
        id = id,
        loanId = loanId,
        familyId = familyId,
        ownerUser = ownerUserId,
        month = month.atDay(1).toEpochDay(),
        amountPaise = amount.paise,
        paidOn = paidOn.toEpochDay(),
        updatedAt = updatedAt.toEpochMilli(),
        deleted = deleted,
    )

fun LoanPaymentEntity.toDto(): LoanPaymentDto =
    LoanPaymentDto(
        id = id,
        loanId = loanId,
        familyId = familyId,
        ownerUser = ownerUser,
        month = LocalDate.ofEpochDay(month).toString(),
        amountPaise = amountPaise,
        paidOn = LocalDate.ofEpochDay(paidOn).toString(),
        deleted = deleted,
    )

fun LoanPaymentDto.toEntity(updatedAtMillis: Long): LoanPaymentEntity =
    LoanPaymentEntity(
        id = id,
        loanId = loanId,
        familyId = familyId,
        ownerUser = ownerUser,
        month = LocalDate.parse(month).toEpochDay(),
        amountPaise = amountPaise,
        paidOn = LocalDate.parse(paidOn).toEpochDay(),
        updatedAt = updatedAtMillis,
        deleted = deleted,
    )
