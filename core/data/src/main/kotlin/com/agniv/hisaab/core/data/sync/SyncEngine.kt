package com.agniv.hisaab.core.data.sync

import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.common.result.resultOf
import com.agniv.hisaab.core.data.mapper.parseServerTime
import com.agniv.hisaab.core.data.mapper.toEntity
import com.agniv.hisaab.core.data.repository.OfflineFirstExpenseRepository.Companion.TABLE_EXPENSES
import com.agniv.hisaab.core.database.HisaabDatabase
import com.agniv.hisaab.core.database.entity.MemberEntity
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.network.ExpenseRemote
import com.agniv.hisaab.core.network.dto.ExpenseDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers how far we have pulled, per family. */
interface SyncStateStore {
    suspend fun lastPulled(familyId: String): String?

    suspend fun setLastPulled(
        familyId: String,
        serverTime: String,
    )
}

/**
 * Push then pull (ARCHITECTURE.md §5).
 * - Push: sync_queue oldest first, in batches; a row leaves the queue only after the server accepted it.
 * - Pull: rows changed on the server since the last pull. Server updated_at is the last-write-wins clock;
 *   a row with a local change still waiting in the queue is skipped, because our newer edit is about to win.
 */
@Singleton
class SyncEngine
    @Inject
    constructor(
        private val db: HisaabDatabase,
        private val remote: ExpenseRemote,
        private val sessions: SessionRepository,
        private val state: SyncStateStore,
        private val dispatchers: DispatcherProvider,
    ) {
        private val json = Json { ignoreUnknownKeys = true }
        private val mutex = Mutex()

        suspend fun sync(): Result<Unit> =
            withContext(dispatchers.io) {
                mutex.withLock {
                    val session = sessions.session.first()
                    val uid = remote.userId()
                    // Phone-only family (not linked to an account yet): nothing to sync with.
                    if (session == null || uid == null || session.userId != uid) return@withLock Result.success(Unit)
                    // A failed push must not block pulling: queued rows are protected from being overwritten.
                    val pushed = resultOf { push() }
                    val pulled =
                        resultOf {
                            pull(session)
                            pullMembers(session)
                        }
                    pushed.exceptionOrNull()?.let { Result.failure(it) } ?: pulled
                }
            }

        private suspend fun push() {
            val queue = db.syncQueueDao()
            while (true) {
                val batch = queue.peek(PUSH_BATCH)
                if (batch.isEmpty()) return
                val expenses = batch.filter { it.tableName == TABLE_EXPENSES }
                resultOf {
                    // Same row edited twice offline: only the newest version needs to go up.
                    val rows =
                        expenses
                            .map { json.decodeFromString(ExpenseDto.serializer(), it.payloadJson) }
                            .associateBy { it.id }
                            .values
                            .toList()
                    if (rows.isNotEmpty()) remote.upsert(rows)
                }.onFailure { batch.forEach { queue.markAttempt(it.id) } }
                    .getOrThrow()
                batch.forEach { queue.remove(it.id) }
            }
        }

        private suspend fun pull(session: Session) {
            var since = state.lastPulled(session.familyId)
            while (true) {
                val rows = remote.changedSince(session.familyId, since, PULL_PAGE)
                if (rows.isEmpty()) return
                for (row in rows) {
                    val serverTime = row.updatedAt ?: continue
                    if (!db.syncQueueDao().hasPending(TABLE_EXPENSES, row.id)) {
                        db.expenseDao().upsert(row.toEntity(parseServerTime(serverTime)))
                    }
                    since = serverTime
                }
                since?.let { state.setLastPulled(session.familyId, it) }
                if (rows.size < PULL_PAGE) return
            }
        }

        private suspend fun pullMembers(session: Session) {
            val now = System.currentTimeMillis()
            val members =
                remote.members(session.familyId).map {
                    MemberEntity(it.id, it.familyId, it.userId, it.name, it.role, now, it.deleted)
                }
            if (members.isNotEmpty()) db.memberDao().upsertAll(members)
        }

        companion object {
            const val PUSH_BATCH = 50
            const val PULL_PAGE = 500
        }
    }
