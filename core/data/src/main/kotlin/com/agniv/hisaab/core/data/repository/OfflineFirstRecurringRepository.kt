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
import com.agniv.hisaab.core.domain.model.Recurring
import com.agniv.hisaab.core.domain.repository.RecurringRepository
import com.agniv.hisaab.core.domain.usecase.ExpenseError
import com.agniv.hisaab.core.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Same pattern as expenses and loans: Room first, sync_queue row in the same transaction. */
class OfflineFirstRecurringRepository
    @Inject
    constructor(
        private val db: HisaabDatabase,
        private val dispatchers: DispatcherProvider,
        private val time: TimeProvider,
        private val sync: SyncRequester,
    ) : RecurringRepository {
        private val dao = db.recurringDao()

        override fun observe(
            familyId: String,
            viewerUserId: String,
        ): Flow<List<Recurring>> =
            dao
                .observeVisible(familyId, viewerUserId)
                .map { rows -> rows.map { it.toDomain() } }
                .flowOn(dispatchers.default)

        override suspend fun getOwned(
            familyId: String,
            userId: String,
        ): List<Recurring> =
            withContext(dispatchers.io) {
                dao.owned(familyId, userId).map { it.toDomain() }
            }

        override suspend fun upsert(recurring: Recurring): Result<Unit> =
            withContext(dispatchers.io) {
                val row = recurring.toEntity()
                resultOf {
                    db.withTransaction {
                        dao.upsert(row)
                        db.syncQueueDao().insert(
                            SyncQueueEntity(
                                tableName = TABLE_RECURRING,
                                rowId = row.id,
                                op = OP_UPSERT,
                                payloadJson = json.encodeToString(row.toDto()),
                                createdAt = time.now().toEpochMilli(),
                            ),
                        )
                    }
                }.onSuccess { sync.requestSync() }
            }

        override suspend fun delete(id: String): Result<Unit> {
            val row = withContext(dispatchers.io) { dao.get(id) } ?: return Result.failure(ExpenseError.NotFound)
            return upsert(row.toDomain().copy(deleted = true, updatedAt = time.now()))
        }

        companion object {
            const val TABLE_RECURRING = "recurring"
            private val json = Json { encodeDefaults = true }
        }
    }
