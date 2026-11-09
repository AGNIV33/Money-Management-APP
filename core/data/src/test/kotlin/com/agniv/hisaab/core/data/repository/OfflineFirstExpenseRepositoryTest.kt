package com.agniv.hisaab.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.database.HisaabDatabase
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.Source
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.util.TimeProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineFirstExpenseRepositoryTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HisaabDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
        }
    private val time =
        object : TimeProvider {
            override fun now(): Instant = Instant.parse("2026-11-09T10:00:00Z")

            override fun today(): LocalDate = LocalDate.of(2026, 11, 9)
        }
    private var syncRequests = 0
    private val repo = OfflineFirstExpenseRepository(db, dispatchers, time) { syncRequests++ }

    @After fun close() = db.close()

    private fun expense(
        id: String,
        owner: String = "u1",
        visibility: Visibility = Visibility.FAMILY,
        date: LocalDate = LocalDate.of(2026, 11, 5),
    ) = Expense(
        id = id,
        familyId = "f1",
        paidBy = "m1",
        ownerUserId = owner,
        amount = Money.ofRupees(120),
        categoryId = "c1",
        date = date,
        note = "doodh",
        mode = PayMode.UPI,
        visibility = visibility,
        source = Source.MANUAL,
        recurringId = null,
        updatedAt = Instant.parse("2026-11-09T10:00:00Z"),
    )

    @Test
    fun `upsert saves row and queues it for sync`() =
        runTest {
            repo.upsert(expense("e1")).getOrThrow()
            assertThat(repo.getExpense("e1")).isEqualTo(expense("e1"))
            val queued = db.syncQueueDao().peek(10)
            assertThat(queued).hasSize(1)
            assertThat(queued.single().rowId).isEqualTo("e1")
            assertThat(queued.single().payloadJson).contains("\"amount_paise\":12000")
            assertThat(queued.single().payloadJson).contains("\"date\":\"2026-11-05\"")
            assertThat(syncRequests).isEqualTo(1)
        }

    @Test
    fun `delete is soft and queued`() =
        runTest {
            repo.upsert(expense("e1")).getOrThrow()
            repo.delete("e1").getOrThrow()
            assertThat(repo.getExpense("e1")?.deleted).isTrue()
            val queued = db.syncQueueDao().peek(10)
            assertThat(queued).hasSize(2)
            assertThat(queued.last().payloadJson).contains("\"deleted\":true")
        }

    @Test
    fun `query hides deleted, other months and other people's private rows`() =
        runTest {
            repo.upsert(expense("mine-private", visibility = Visibility.PRIVATE)).getOrThrow()
            repo.upsert(expense("theirs-private", owner = "u2", visibility = Visibility.PRIVATE)).getOrThrow()
            repo.upsert(expense("theirs-family", owner = "u2")).getOrThrow()
            repo.upsert(expense("old", date = LocalDate.of(2026, 10, 31))).getOrThrow()
            repo.upsert(expense("gone")).getOrThrow()
            repo.delete("gone").getOrThrow()

            repo.observeExpenses("f1", "u1", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).test {
                assertThat(awaitItem().map { it.id }).containsExactly("mine-private", "theirs-family")
            }
        }
}
