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
import com.agniv.hisaab.core.domain.model.Udhaar
import com.agniv.hisaab.core.domain.model.UdhaarPayment
import com.agniv.hisaab.core.domain.repository.UdhaarRepository
import com.agniv.hisaab.core.domain.repository.UdhaarWithPayments
import com.agniv.hisaab.core.domain.usecase.UdhaarError
import com.agniv.hisaab.core.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Same pattern as loans: Room first, sync_queue row in the same transaction. */
class OfflineFirstUdhaarRepository
    @Inject
    constructor(
        private val db: HisaabDatabase,
        private val dispatchers: DispatcherProvider,
        private val time: TimeProvider,
        private val sync: SyncRequester,
    ) : UdhaarRepository {
        private val dao = db.udhaarDao()

        override fun observe(
            familyId: String,
            viewerUserId: String,
        ): Flow<List<UdhaarWithPayments>> =
            combine(
                dao.observeVisible(familyId, viewerUserId),
                dao.observeVisiblePayments(familyId, viewerUserId),
            ) { entries, payments ->
                val byEntry = payments.groupBy { it.udhaarId }
                entries.map { row -> UdhaarWithPayments(row.toDomain(), byEntry[row.id].orEmpty().map { it.toDomain() }) }
            }.flowOn(dispatchers.default)

        override suspend fun get(id: String): Udhaar? =
            withContext(dispatchers.io) {
                dao.get(id)?.toDomain()
            }

        override suspend fun upsert(udhaar: Udhaar): Result<Unit> {
            val row = udhaar.toEntity()
            return write(TABLE_UDHAAR, row.id, json.encodeToString(row.toDto())) { dao.upsert(row) }
        }

        override suspend fun delete(id: String): Result<Unit> {
            val entry = get(id) ?: return Result.failure(UdhaarError.NotFound)
            return upsert(entry.copy(deleted = true, updatedAt = time.now()))
        }

        override suspend fun savePayment(payment: UdhaarPayment): Result<Unit> {
            val row = payment.toEntity()
            return write(TABLE_UDHAAR_PAYMENTS, row.id, json.encodeToString(row.toDto())) { dao.upsertPayment(row) }
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
            const val TABLE_UDHAAR = "udhaar"
            const val TABLE_UDHAAR_PAYMENTS = "udhaar_payments"
            private val json = Json { encodeDefaults = true }
        }
    }
