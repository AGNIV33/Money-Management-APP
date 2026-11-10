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
import com.agniv.hisaab.core.domain.model.Loan
import com.agniv.hisaab.core.domain.model.LoanPayment
import com.agniv.hisaab.core.domain.repository.LoanRepository
import com.agniv.hisaab.core.domain.repository.LoanWithPayments
import com.agniv.hisaab.core.domain.usecase.LoanError
import com.agniv.hisaab.core.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Same pattern as expenses: Room first, and the sync_queue row is written in the same transaction. */
class OfflineFirstLoanRepository
    @Inject
    constructor(
        private val db: HisaabDatabase,
        private val dispatchers: DispatcherProvider,
        private val time: TimeProvider,
        private val sync: SyncRequester,
    ) : LoanRepository {
        private val loanDao = db.loanDao()
        private val queueDao = db.syncQueueDao()

        override fun observeLoans(
            familyId: String,
            viewerUserId: String,
        ): Flow<List<LoanWithPayments>> =
            combine(
                loanDao.observeVisible(familyId, viewerUserId),
                loanDao.observeVisiblePayments(familyId, viewerUserId),
            ) { loans, payments ->
                val byLoan = payments.groupBy { it.loanId }
                loans.map { row -> LoanWithPayments(row.toDomain(), byLoan[row.id].orEmpty().map { it.toDomain() }) }
            }.flowOn(dispatchers.default)

        override suspend fun getLoan(id: String): Loan? =
            withContext(dispatchers.io) {
                loanDao.get(id)?.toDomain()
            }

        override suspend fun upsert(loan: Loan): Result<Unit> {
            val row = loan.toEntity()
            return write(TABLE_LOANS, row.id, json.encodeToString(row.toDto())) { loanDao.upsert(row) }
        }

        override suspend fun delete(id: String): Result<Unit> {
            val loan = getLoan(id) ?: return Result.failure(LoanError.NotFound)
            return upsert(loan.copy(deleted = true, updatedAt = time.now()))
        }

        override suspend fun savePayment(payment: LoanPayment): Result<Unit> {
            val row = payment.toEntity()
            return write(TABLE_LOAN_PAYMENTS, row.id, json.encodeToString(row.toDto())) { loanDao.upsertPayment(row) }
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
                        queueDao.insert(
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
            const val TABLE_LOANS = "loans"
            const val TABLE_LOAN_PAYMENTS = "loan_payments"
            private val json = Json { encodeDefaults = true }
        }
    }
