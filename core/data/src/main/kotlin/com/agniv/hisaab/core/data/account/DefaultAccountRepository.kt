package com.agniv.hisaab.core.data.account

import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.common.result.resultOf
import com.agniv.hisaab.core.data.session.LocalSessionRepository
import com.agniv.hisaab.core.data.sync.SyncEngine
import com.agniv.hisaab.core.database.HisaabDatabase
import com.agniv.hisaab.core.domain.model.Role
import com.agniv.hisaab.core.domain.repository.AccountError
import com.agniv.hisaab.core.domain.repository.AccountRepository
import com.agniv.hisaab.core.domain.repository.AuthRepository
import com.agniv.hisaab.core.domain.util.TimeProvider
import com.agniv.hisaab.core.network.FamilyApi
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val MAX_NAME = 40

class DefaultAccountRepository
    @Inject
    constructor(
        private val db: HisaabDatabase,
        private val api: FamilyApi,
        private val auth: AuthRepository,
        private val sync: SyncEngine,
        private val session: LocalSessionRepository,
        private val time: TimeProvider,
        private val dispatchers: DispatcherProvider,
    ) : AccountRepository {
        override suspend fun renameMe(
            memberId: String,
            name: String,
        ): Result<Unit> {
            val clean = name.trim()
            if (clean.isEmpty() || clean.length > MAX_NAME) return Result.failure(AccountError.BadName)
            return change { api.updateMember(memberId, name = clean) }.onSuccess {
                db.memberDao().get(memberId)?.let { db.memberDao().upsert(it.copy(name = clean, updatedAt = time.now().toEpochMilli())) }
            }
        }

        override suspend fun setRole(
            memberId: String,
            role: Role,
        ): Result<Unit> =
            change { api.updateMember(memberId, role = role.name) }.onSuccess {
                db
                    .memberDao()
                    .get(
                        memberId,
                    )?.let { db.memberDao().upsert(it.copy(role = role.name, updatedAt = time.now().toEpochMilli())) }
            }

        override suspend fun removeMember(memberId: String): Result<Unit> =
            change { api.updateMember(memberId, deleted = true) }.onSuccess {
                db.memberDao().get(memberId)?.let { db.memberDao().upsert(it.copy(deleted = true, updatedAt = time.now().toEpochMilli())) }
            }

        override suspend fun pendingChanges(): Int = withContext(dispatchers.io) { db.syncQueueDao().observeCount().first() }

        override suspend fun signOut(
            force: Boolean,
            localFamilyName: String,
            localMemberName: String,
        ): Result<Unit> =
            withContext(dispatchers.io) {
                // Best effort: push everything still waiting before the phone forgets it.
                sync.sync()
                val pending = db.syncQueueDao().observeCount().first()
                if (pending > 0 && !force) return@withContext Result.failure(AccountError.PendingChanges(pending))
                auth.signOut()
                db.clearAllTables()
                session.resetLocal(localFamilyName, localMemberName)
                Result.success(Unit)
            }

        override suspend fun hasServerAccount(): Boolean = api.currentUserId() != null

        override suspend fun deleteServerAccount(): Result<Unit> = change { api.deleteMyAccount() }

        override suspend fun wipeThisPhone(
            localFamilyName: String,
            localMemberName: String,
        ) {
            withContext(dispatchers.io) {
                // The login is already gone on the server, so this only drops the token kept on the phone.
                auth.signOut()
                db.clearAllTables()
                session.resetLocal(localFamilyName, localMemberName)
            }
        }

        private suspend fun change(
            block: suspend () -> Unit,
        ): Result<Unit> =
            withContext(dispatchers.io) {
                resultOf { block() }.recoverCatching { throw it.toAccountError() }
            }

        private fun Throwable.toAccountError(): Throwable =
            when {
                this is HttpRequestException -> AccountError.Network
                this is RestException && message.orEmpty().contains("at least one admin") -> AccountError.LastAdmin
                this is RestException -> AccountError.NotAllowed
                else -> this
            }
    }
