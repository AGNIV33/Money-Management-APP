package com.agniv.hisaab.core.data.repository

import androidx.room.withTransaction
import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.common.result.resultOf
import com.agniv.hisaab.core.data.mapper.toDomain
import com.agniv.hisaab.core.data.mapper.toDto
import com.agniv.hisaab.core.data.mapper.toEntity
import com.agniv.hisaab.core.database.HisaabDatabase
import com.agniv.hisaab.core.database.entity.ExpenseEntity
import com.agniv.hisaab.core.database.entity.SyncQueueEntity
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.repository.ExpenseRepository
import com.agniv.hisaab.core.domain.usecase.ExpenseError
import com.agniv.hisaab.core.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject

/**
 * Room is the source of truth (ARCHITECTURE.md §1). Every write also adds a sync_queue row
 * in the same transaction, so a crash can never save an expense without queueing its upload.
 */
class OfflineFirstExpenseRepository
    @Inject
    constructor(
        private val db: HisaabDatabase,
        private val dispatchers: DispatcherProvider,
        private val time: TimeProvider,
    ) : ExpenseRepository {
        private val expenseDao = db.expenseDao()
        private val queueDao = db.syncQueueDao()

        override fun observeExpenses(
            familyId: String,
            viewerUserId: String,
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<Expense>> =
            expenseDao
                .observeRange(familyId, viewerUserId, from.toEpochDay(), to.toEpochDay())
                .map { rows -> rows.map { it.toDomain() } }
                .flowOn(dispatchers.default)

        override suspend fun getExpense(id: String): Expense? =
            withContext(dispatchers.io) {
                expenseDao.get(id)?.toDomain()
            }

        override suspend fun upsert(expense: Expense): Result<Unit> = save(expense.toEntity())

        override suspend fun delete(id: String): Result<Unit> =
            withContext(dispatchers.io) {
                val row = expenseDao.get(id) ?: return@withContext Result.failure(ExpenseError.NotFound)
                save(row.copy(deleted = true, updatedAt = time.now().toEpochMilli()))
            }

        private suspend fun save(row: ExpenseEntity): Result<Unit> =
            withContext(dispatchers.io) {
                resultOf {
                    db.withTransaction {
                        expenseDao.upsert(row)
                        queueDao.insert(
                            SyncQueueEntity(
                                tableName = TABLE_EXPENSES,
                                rowId = row.id,
                                op = OP_UPSERT,
                                payloadJson = json.encodeToString(row.toDto()),
                                createdAt = time.now().toEpochMilli(),
                            ),
                        )
                    }
                }
            }

        companion object {
            const val TABLE_EXPENSES = "expenses"

            // Soft deletes are pushed as an upsert with deleted = true.
            const val OP_UPSERT = "UPSERT"

            private val json = Json { encodeDefaults = true }
        }
    }
