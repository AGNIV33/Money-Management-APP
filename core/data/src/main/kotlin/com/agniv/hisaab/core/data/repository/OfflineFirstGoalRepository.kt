package com.agniv.hisaab.core.data.repository

import androidx.room.withTransaction
import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.common.result.resultOf
import com.agniv.hisaab.core.data.mapper.toDomain
import com.agniv.hisaab.core.data.mapper.toDto
import com.agniv.hisaab.core.data.mapper.toEntity
import com.agniv.hisaab.core.data.repository.OfflineFirstExpenseRepository.Companion.OP_UPSERT
import com.agniv.hisaab.core.data.sync.SyncRequester
import com.agniv.hisaab.core.database.HisaabDatabase
import com.agniv.hisaab.core.database.entity.SyncQueueEntity
import com.agniv.hisaab.core.domain.model.Goal
import com.agniv.hisaab.core.domain.model.GoalContribution
import com.agniv.hisaab.core.domain.repository.GoalRepository
import com.agniv.hisaab.core.domain.repository.GoalWithContributions
import com.agniv.hisaab.core.domain.usecase.GoalError
import com.agniv.hisaab.core.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Same pattern as udhaar: Room first, sync_queue row in the same transaction. */
class OfflineFirstGoalRepository
    @Inject
    constructor(
        private val db: HisaabDatabase,
        private val dispatchers: DispatcherProvider,
        private val time: TimeProvider,
        private val sync: SyncRequester,
    ) : GoalRepository {
        private val dao = db.goalDao()

        override fun observe(
            familyId: String,
            viewerUserId: String,
        ): Flow<List<GoalWithContributions>> =
            combine(
                dao.observeVisible(familyId, viewerUserId),
                dao.observeVisibleContributions(familyId, viewerUserId),
            ) { goals, entries ->
                val byGoal = entries.groupBy { it.goalId }
                goals.map { row -> GoalWithContributions(row.toDomain(), byGoal[row.id].orEmpty().map { it.toDomain() }) }
            }.flowOn(dispatchers.default)

        override suspend fun get(id: String): Goal? =
            withContext(dispatchers.io) {
                dao.get(id)?.toDomain()
            }

        override suspend fun upsert(goal: Goal): Result<Unit> {
            val row = goal.toEntity()
            return write(TABLE_GOALS, row.id, json.encodeToString(row.toDto())) { dao.upsert(row) }
        }

        override suspend fun delete(id: String): Result<Unit> {
            val goal = get(id) ?: return Result.failure(GoalError.NotFound)
            return upsert(goal.copy(deleted = true, updatedAt = time.now()))
        }

        override suspend fun saveContribution(contribution: GoalContribution): Result<Unit> {
            val row = contribution.toEntity()
            return write(TABLE_GOAL_CONTRIBUTIONS, row.id, json.encodeToString(row.toDto())) { dao.upsertContribution(row) }
        }

        private suspend fun write(
            table: String,
            rowId: String,
            payload: String,
            save: suspend () -> Unit,
        ): Result<Unit> =
            withContext(dispatchers.io) {
                resultOf {
                    db.withTransaction {
                        save()
                        db.syncQueueDao().insert(
                            SyncQueueEntity(
                                tableName = table,
                                rowId = rowId,
                                op = OP_UPSERT,
                                payloadJson = payload,
                                createdAt = time.now().toEpochMilli(),
                            ),
                        )
                    }
                }.onSuccess { sync.requestSync() }
            }

        companion object {
            const val TABLE_GOALS = "goals"
            const val TABLE_GOAL_CONTRIBUTIONS = "goal_contributions"
            private val json = Json { encodeDefaults = true }
        }
    }
