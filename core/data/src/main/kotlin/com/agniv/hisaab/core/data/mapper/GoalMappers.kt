package com.agniv.hisaab.core.data.mapper

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.database.entity.GoalContributionEntity
import com.agniv.hisaab.core.database.entity.GoalEntity
import com.agniv.hisaab.core.domain.model.Goal
import com.agniv.hisaab.core.domain.model.GoalContribution
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.network.dto.GoalContributionDto
import com.agniv.hisaab.core.network.dto.GoalDto
import java.time.Instant
import java.time.LocalDate

fun GoalEntity.toDomain(): Goal =
    Goal(
        id = id,
        familyId = familyId,
        ownerUserId = ownerUser,
        name = name,
        target = Money(targetPaise),
        deadline = deadline?.let(LocalDate::ofEpochDay),
        visibility = enumOr(visibility, Visibility.PRIVATE),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        deleted = deleted,
    )

fun Goal.toEntity(): GoalEntity =
    GoalEntity(
        id = id,
        familyId = familyId,
        ownerUser = ownerUserId,
        name = name,
        targetPaise = target.paise,
        deadline = deadline?.toEpochDay(),
        visibility = visibility.name,
        updatedAt = updatedAt.toEpochMilli(),
        deleted = deleted,
    )

fun GoalEntity.toDto(): GoalDto =
    GoalDto(
        id = id,
        familyId = familyId,
        ownerUser = ownerUser,
        name = name,
        targetPaise = targetPaise,
        deadline = deadline?.let { LocalDate.ofEpochDay(it).toString() },
        visibility = visibility,
        deleted = deleted,
    )

fun GoalDto.toEntity(updatedAtMillis: Long): GoalEntity =
    GoalEntity(
        id = id,
        familyId = familyId,
        ownerUser = ownerUser,
        name = name,
        targetPaise = targetPaise,
        deadline = deadline?.let { LocalDate.parse(it).toEpochDay() },
        visibility = visibility,
        updatedAt = updatedAtMillis,
        deleted = deleted,
    )

fun GoalContributionEntity.toDomain(): GoalContribution =
    GoalContribution(
        id = id,
        goalId = goalId,
        familyId = familyId,
        ownerUserId = ownerUser,
        amount = Money(amountPaise),
        date = LocalDate.ofEpochDay(date),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        deleted = deleted,
    )

fun GoalContribution.toEntity(): GoalContributionEntity =
    GoalContributionEntity(
        id = id,
        goalId = goalId,
        familyId = familyId,
        ownerUser = ownerUserId,
        amountPaise = amount.paise,
        date = date.toEpochDay(),
        updatedAt = updatedAt.toEpochMilli(),
        deleted = deleted,
    )

fun GoalContributionEntity.toDto(): GoalContributionDto =
    GoalContributionDto(
        id = id,
        goalId = goalId,
        familyId = familyId,
        ownerUser = ownerUser,
        amountPaise = amountPaise,
        date = LocalDate.ofEpochDay(date).toString(),
        deleted = deleted,
    )

fun GoalContributionDto.toEntity(updatedAtMillis: Long): GoalContributionEntity =
    GoalContributionEntity(
        id = id,
        goalId = goalId,
        familyId = familyId,
        ownerUser = ownerUser,
        amountPaise = amountPaise,
        date = LocalDate.parse(date).toEpochDay(),
        updatedAt = updatedAtMillis,
        deleted = deleted,
    )
