package com.agniv.hisaab.core.data.family

import androidx.room.withTransaction
import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.result.resultOf
import com.agniv.hisaab.core.data.mapper.toDto
import com.agniv.hisaab.core.data.repository.OfflineFirstExpenseRepository
import com.agniv.hisaab.core.data.repository.OfflineFirstLoanRepository
import com.agniv.hisaab.core.data.session.LocalSessionRepository
import com.agniv.hisaab.core.data.sync.BudgetChange
import com.agniv.hisaab.core.data.sync.SyncRequester
import com.agniv.hisaab.core.data.sync.TABLE_FAMILY_BUDGET
import com.agniv.hisaab.core.database.HisaabDatabase
import com.agniv.hisaab.core.database.entity.FamilyEntity
import com.agniv.hisaab.core.database.entity.MemberEntity
import com.agniv.hisaab.core.database.entity.SyncQueueEntity
import com.agniv.hisaab.core.domain.model.Family
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.repository.FamilyError
import com.agniv.hisaab.core.domain.repository.FamilyRepository
import com.agniv.hisaab.core.domain.util.TimeProvider
import com.agniv.hisaab.core.network.FamilyApi
import com.agniv.hisaab.core.network.dto.FamilyDto
import com.agniv.hisaab.core.network.dto.LoanDto
import com.agniv.hisaab.core.network.dto.LoanPaymentDto
import com.agniv.hisaab.core.network.dto.MemberDto
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the phone-only family (made before login) into a real Supabase family.
 * Whatever path is taken, expenses already saved on the phone are kept and re-queued for upload.
 */
@Singleton
class LinkingFamilyRepository
    @Inject
    constructor(
        private val db: HisaabDatabase,
        private val api: FamilyApi,
        private val sessions: LocalSessionRepository,
        private val time: TimeProvider,
        private val dispatchers: DispatcherProvider,
        private val sync: SyncRequester,
    ) : FamilyRepository {
        private val json = Json { encodeDefaults = true }

        override fun observeFamily(familyId: String): Flow<Family?> =
            db.familyDao().observe(familyId).map { e ->
                e?.let { Family(it.id, it.name, it.inviteCode, it.monthlyBudgetPaise?.let(::Money)) }
            }

        override suspend fun linkExistingFamily(): Result<Boolean> =
            call {
                val membership = api.myMembership() ?: return@call false
                val family = api.family(membership.familyId) ?: return@call false
                adopt(family, membership)
                true
            }

        override suspend fun createFamily(
            familyName: String,
            myName: String,
        ): Result<Unit> =
            call {
                val local = sessions.session.filterNotNull().first()
                // Re-use the phone's ids so nothing saved so far needs new ids.
                val family = api.createFamily(local.familyId, local.memberId, familyName.trim(), myName.trim())
                val me = MemberDto(local.memberId, family.id, requireUser(), myName.trim(), "ADMIN")
                adopt(family, me)
            }

        override suspend fun joinFamily(
            code: String,
            myName: String,
        ): Result<Unit> =
            call {
                val local = sessions.session.filterNotNull().first()
                val family = api.joinFamily(code.trim().uppercase(), local.memberId, myName.trim())
                val me =
                    api.members(family.id).firstOrNull { it.userId == requireUser() }
                        ?: MemberDto(local.memberId, family.id, requireUser(), myName.trim(), "MEMBER")
                adopt(family, me)
            }

        override suspend fun setMonthlyBudget(
            familyId: String,
            budget: Money?,
        ): Result<Unit> =
            withContext(dispatchers.io) {
                resultOf {
                    db.withTransaction {
                        val family = db.familyDao().get(familyId) ?: throw FamilyError.NotSignedIn
                        val now = time.now().toEpochMilli()
                        db.familyDao().upsert(family.copy(monthlyBudgetPaise = budget?.paise, updatedAt = now))
                        db.syncQueueDao().insert(
                            SyncQueueEntity(
                                tableName = TABLE_FAMILY_BUDGET,
                                rowId = familyId,
                                op = OfflineFirstExpenseRepository.OP_UPSERT,
                                payloadJson = json.encodeToString(BudgetChange.serializer(), BudgetChange(familyId, budget?.paise)),
                                createdAt = now,
                            ),
                        )
                    }
                }.onSuccess { sync.requestSync() }
            }

        /** Stores the server family + members locally, moves local expenses into it, and switches the session. */
        private suspend fun adopt(
            family: FamilyDto,
            me: MemberDto,
        ) {
            val local = sessions.session.filterNotNull().first()
            val members = runCatching { api.members(family.id) }.getOrDefault(listOf(me))
            val now = time.now().toEpochMilli()
            db.withTransaction {
                db.familyDao().upsert(
                    FamilyEntity(family.id, family.name, family.inviteCode, family.monthlyBudgetPaise, now, family.deleted),
                )
                db.memberDao().upsertAll(
                    (members + me).distinctBy { it.id }.map {
                        MemberEntity(
                            it.id,
                            it.familyId,
                            it.userId,
                            it.name,
                            it.role,
                            now,
                            it.deleted,
                        )
                    },
                )
                db.expenseDao().reassign(
                    oldFamilyId = local.familyId,
                    newFamilyId = family.id,
                    newOwner = me.userId,
                    oldMemberId = local.memberId,
                    newMemberId = me.id,
                )
                db.loanDao().reassign(local.familyId, family.id, me.userId)
                db.loanDao().reassignPayments(local.familyId, family.id, me.userId)
                if (local.familyId != family.id) {
                    db.memberDao().deleteForFamily(local.familyId)
                    db.familyDao().delete(local.familyId)
                }
                // Old queue entries carry the phone-only owner id; queue everything again with the real one.
                requeueAll(family.id, now)
            }
            sessions.replace(Session(userId = me.userId, familyId = family.id, memberId = me.id))
            sync.requestSync()
        }

        /** Must run inside the adopt transaction. */
        private suspend fun requeueAll(
            familyId: String,
            now: Long,
        ) {
            val queue = db.syncQueueDao()
            queue.clear()

            suspend fun add(
                table: String,
                rowId: String,
                payload: String,
            ) = queue.insert(
                SyncQueueEntity(
                    tableName = table,
                    rowId = rowId,
                    op = OfflineFirstExpenseRepository.OP_UPSERT,
                    payloadJson = payload,
                    createdAt = now,
                ),
            )
            db.expenseDao().allForFamily(familyId).forEach {
                add(OfflineFirstExpenseRepository.TABLE_EXPENSES, it.id, json.encodeToString(it.toDto()))
            }
            // Loans before payments, the order the server needs.
            db.loanDao().allForFamily(familyId).forEach {
                add(OfflineFirstLoanRepository.TABLE_LOANS, it.id, json.encodeToString(LoanDto.serializer(), it.toDto()))
            }
            db.loanDao().allPaymentsForFamily(familyId).forEach {
                add(OfflineFirstLoanRepository.TABLE_LOAN_PAYMENTS, it.id, json.encodeToString(LoanPaymentDto.serializer(), it.toDto()))
            }
        }

        private fun requireUser(): String = api.currentUserId() ?: throw FamilyError.NotSignedIn

        private suspend fun <T> call(block: suspend () -> T): Result<T> =
            withContext(dispatchers.io) {
                resultOf { block() }.recoverCatching { throw it.toFamilyError() }
            }

        private fun Throwable.toFamilyError(): Throwable =
            when {
                this is HttpRequestException -> FamilyError.Network
                this is RestException && message.orEmpty().contains("invalid invite code") -> FamilyError.InvalidCode
                this is RestException && message.orEmpty().contains("family is full") -> FamilyError.Full
                else -> this
            }
    }
