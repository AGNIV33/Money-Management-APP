package com.agniv.hisaab.core.data.sync

import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.common.result.resultOf
import com.agniv.hisaab.core.data.mapper.parseServerTime
import com.agniv.hisaab.core.data.mapper.toEntity
import com.agniv.hisaab.core.data.repository.OfflineFirstExpenseRepository.Companion.TABLE_EXPENSES
import com.agniv.hisaab.core.data.repository.OfflineFirstGoalRepository.Companion.TABLE_GOALS
import com.agniv.hisaab.core.data.repository.OfflineFirstGoalRepository.Companion.TABLE_GOAL_CONTRIBUTIONS
import com.agniv.hisaab.core.data.repository.OfflineFirstLoanRepository.Companion.TABLE_LOANS
import com.agniv.hisaab.core.data.repository.OfflineFirstLoanRepository.Companion.TABLE_LOAN_PAYMENTS
import com.agniv.hisaab.core.data.repository.OfflineFirstRecurringRepository.Companion.TABLE_RECURRING
import com.agniv.hisaab.core.data.repository.OfflineFirstUdhaarRepository.Companion.TABLE_UDHAAR
import com.agniv.hisaab.core.data.repository.OfflineFirstUdhaarRepository.Companion.TABLE_UDHAAR_PAYMENTS
import com.agniv.hisaab.core.database.HisaabDatabase
import com.agniv.hisaab.core.database.entity.FamilyEntity
import com.agniv.hisaab.core.database.entity.MemberEntity
import com.agniv.hisaab.core.database.entity.SyncQueueEntity
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.network.ExpenseRemote
import com.agniv.hisaab.core.network.GoalRemote
import com.agniv.hisaab.core.network.LoanRemote
import com.agniv.hisaab.core.network.RecurringRemote
import com.agniv.hisaab.core.network.UdhaarRemote
import com.agniv.hisaab.core.network.dto.ExpenseDto
import com.agniv.hisaab.core.network.dto.GoalContributionDto
import com.agniv.hisaab.core.network.dto.GoalDto
import com.agniv.hisaab.core.network.dto.LoanDto
import com.agniv.hisaab.core.network.dto.LoanPaymentDto
import com.agniv.hisaab.core.network.dto.RecurringDto
import com.agniv.hisaab.core.network.dto.UdhaarDto
import com.agniv.hisaab.core.network.dto.UdhaarPaymentDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Queue payload for a monthly-budget change (table name [TABLE_FAMILY_BUDGET]). */
@kotlinx.serialization.Serializable
data class BudgetChange(
    val familyId: String,
    val paise: Long?,
)

const val TABLE_FAMILY_BUDGET = "family_budget"

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
 *
 * One small pull function per table (they share pullTable), so the function-count rule is switched off.
 */
@Suppress("TooManyFunctions")
@Singleton
class SyncEngine
    @Inject
    constructor(
        private val db: HisaabDatabase,
        private val remote: ExpenseRemote,
        private val loanRemote: LoanRemote,
        private val recurringRemote: RecurringRemote,
        private val udhaarRemote: UdhaarRemote,
        private val goalRemote: GoalRemote,
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
                            pullLoans(session)
                            pullRecurring(session)
                            pullUdhaar(session)
                            pullGoals(session)
                            pullMembers(session)
                            pullFamily(session)
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
                val budgets = batch.filter { it.tableName == TABLE_FAMILY_BUDGET }
                resultOf {
                    budgets.lastOrNull()?.let {
                        val change = json.decodeFromString(BudgetChange.serializer(), it.payloadJson)
                        remote.setMonthlyBudget(change.familyId, change.paise)
                    }
                    // Same row edited twice offline: only the newest version needs to go up.
                    val rows =
                        expenses
                            .map { json.decodeFromString(ExpenseDto.serializer(), it.payloadJson) }
                            .associateBy { it.id }
                            .values
                            .toList()
                    if (rows.isNotEmpty()) remote.upsert(rows)
                    // Loans before payments: a payment row points at its loan on the server.
                    batch.latest(TABLE_LOANS, LoanDto.serializer())?.let { loanRemote.upsertLoans(it) }
                    batch.latest(TABLE_LOAN_PAYMENTS, LoanPaymentDto.serializer())?.let { loanRemote.upsertPayments(it) }
                    batch.latest(TABLE_RECURRING, RecurringDto.serializer())?.let { recurringRemote.upsert(it) }
                    // Entries before their payments: a payment row points at its entry on the server.
                    batch.latest(TABLE_UDHAAR, UdhaarDto.serializer())?.let { udhaarRemote.upsertEntries(it) }
                    batch.latest(TABLE_UDHAAR_PAYMENTS, UdhaarPaymentDto.serializer())?.let { udhaarRemote.upsertPayments(it) }
                    // Goals before their savings entries, same reason.
                    batch.latest(TABLE_GOALS, GoalDto.serializer())?.let { goalRemote.upsertGoals(it) }
                    batch.latest(TABLE_GOAL_CONTRIBUTIONS, GoalContributionDto.serializer())?.let { goalRemote.upsertContributions(it) }
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

        private fun <T> List<SyncQueueEntity>.latest(
            table: String,
            serializer: KSerializer<T>,
        ): List<T>? =
            filter { it.tableName == table }
                .associateBy { it.rowId }
                .values
                .map { json.decodeFromString(serializer, it.payloadJson) }
                .ifEmpty { null }

        private suspend fun pullLoans(session: Session) {
            pullTable(
                key = session.familyId + LOANS_KEY,
                fetch = { since -> loanRemote.loansChangedSince(session.familyId, since, PULL_PAGE) },
                stamp = { it.updatedAt },
            ) { row, time ->
                if (!db.syncQueueDao().hasPending(TABLE_LOANS, row.id)) db.loanDao().upsert(row.toEntity(time))
            }
            pullTable(
                key = session.familyId + PAYMENTS_KEY,
                fetch = { since -> loanRemote.paymentsChangedSince(session.familyId, since, PULL_PAGE) },
                stamp = { it.updatedAt },
            ) { row, time ->
                if (!db.syncQueueDao().hasPending(TABLE_LOAN_PAYMENTS, row.id)) db.loanDao().upsertPayment(row.toEntity(time))
            }
        }

        private suspend fun pullGoals(session: Session) {
            pullTable(
                key = session.familyId + GOALS_KEY,
                fetch = { since -> goalRemote.goalsChangedSince(session.familyId, since, PULL_PAGE) },
                stamp = { it.updatedAt },
            ) { row, time ->
                if (!db.syncQueueDao().hasPending(TABLE_GOALS, row.id)) db.goalDao().upsert(row.toEntity(time))
            }
            pullTable(
                key = session.familyId + GOAL_CONTRIBUTIONS_KEY,
                fetch = { since -> goalRemote.contributionsChangedSince(session.familyId, since, PULL_PAGE) },
                stamp = { it.updatedAt },
            ) { row, time ->
                if (!db.syncQueueDao().hasPending(TABLE_GOAL_CONTRIBUTIONS, row.id)) db.goalDao().upsertContribution(row.toEntity(time))
            }
        }

        private suspend fun pullUdhaar(session: Session) {
            pullTable(
                key = session.familyId + UDHAAR_KEY,
                fetch = { since -> udhaarRemote.entriesChangedSince(session.familyId, since, PULL_PAGE) },
                stamp = { it.updatedAt },
            ) { row, time ->
                if (!db.syncQueueDao().hasPending(TABLE_UDHAAR, row.id)) db.udhaarDao().upsert(row.toEntity(time))
            }
            pullTable(
                key = session.familyId + UDHAAR_PAYMENTS_KEY,
                fetch = { since -> udhaarRemote.paymentsChangedSince(session.familyId, since, PULL_PAGE) },
                stamp = { it.updatedAt },
            ) { row, time ->
                if (!db.syncQueueDao().hasPending(TABLE_UDHAAR_PAYMENTS, row.id)) db.udhaarDao().upsertPayment(row.toEntity(time))
            }
        }

        private suspend fun pullRecurring(session: Session) {
            pullTable(
                key = session.familyId + RECURRING_KEY,
                fetch = { since -> recurringRemote.changedSince(session.familyId, since, PULL_PAGE) },
                stamp = { it.updatedAt },
            ) { row, time ->
                if (!db.syncQueueDao().hasPending(TABLE_RECURRING, row.id)) db.recurringDao().upsert(row.toEntity(time))
            }
        }

        /** Pages through rows changed since the saved server time and remembers how far it got. */
        private suspend fun <T> pullTable(
            key: String,
            fetch: suspend (String?) -> List<T>,
            stamp: (T) -> String?,
            store: suspend (T, Long) -> Unit,
        ) {
            var since = state.lastPulled(key)
            while (true) {
                val rows = fetch(since)
                if (rows.isEmpty()) return
                for (row in rows) {
                    val serverTime = stamp(row) ?: continue
                    store(row, parseServerTime(serverTime))
                    since = serverTime
                }
                since?.let { state.setLastPulled(key, it) }
                if (rows.size < PULL_PAGE) return
            }
        }

        private suspend fun pullFamily(session: Session) {
            if (db.syncQueueDao().hasPending(TABLE_FAMILY_BUDGET, session.familyId)) return
            val family = remote.family(session.familyId) ?: return
            db.familyDao().upsert(
                FamilyEntity(
                    family.id,
                    family.name,
                    family.inviteCode,
                    family.monthlyBudgetPaise,
                    System.currentTimeMillis(),
                    family.deleted,
                ),
            )
        }

        private suspend fun pullMembers(session: Session) {
            val now = System.currentTimeMillis()
            val members =
                remote.members(session.familyId).map {
                    MemberEntity(it.id, it.familyId, it.userId.orEmpty(), it.name, it.role, now, it.deleted)
                }
            if (members.isNotEmpty()) db.memberDao().upsertAll(members)
        }

        companion object {
            const val PUSH_BATCH = 50
            const val PULL_PAGE = 500

            // Loans keep their own pull clock next to the expenses one (same store, keyed per family).
            private const val LOANS_KEY = ":loans"
            private const val PAYMENTS_KEY = ":loan_payments"
            private const val RECURRING_KEY = ":recurring"
            private const val UDHAAR_KEY = ":udhaar"
            private const val UDHAAR_PAYMENTS_KEY = ":udhaar_payments"
            private const val GOALS_KEY = ":goals"
            private const val GOAL_CONTRIBUTIONS_KEY = ":goal_contributions"
        }
    }
