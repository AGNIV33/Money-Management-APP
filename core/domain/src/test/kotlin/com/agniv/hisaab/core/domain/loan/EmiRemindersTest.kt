package com.agniv.hisaab.core.domain.loan

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Loan
import com.agniv.hisaab.core.domain.model.LoanType
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.usecase.LoanItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class EmiRemindersTest {
    private fun item(
        owner: String = "u1",
        paid: Set<YearMonth> = emptySet(),
        today: LocalDate,
    ): LoanItem {
        val loan =
            Loan(
                "l1",
                "f1",
                owner,
                LoanType.CAR,
                "SBI",
                Money.ofRupees(100_000),
                1_200,
                12,
                Money.ofRupees(8_885),
                LocalDate.of(2026, 12, 5),
                5,
                LocalDate.of(2026, 11, 1),
                Visibility.FAMILY,
                Instant.EPOCH,
            )
        return LoanItem(loan, LoanProgress.of(loan, paid, today), paid)
    }

    private fun stageOn(day: Int): ReminderStage? {
        val today = LocalDate.of(2026, 12, day)
        return EmiReminders.due(listOf(item(today = today)), "u1").singleOrNull()?.stage
    }

    @Test
    fun `stages by days left`() {
        assertThat(stageOn(1)).isNull() // 4 days left
        assertThat(stageOn(2)).isEqualTo(ReminderStage.THREE_DAYS)
        assertThat(stageOn(3)).isEqualTo(ReminderStage.THREE_DAYS) // check ran a day late
        assertThat(stageOn(4)).isEqualTo(ReminderStage.ONE_DAY)
        assertThat(stageOn(5)).isEqualTo(ReminderStage.TODAY)
        assertThat(stageOn(6)).isNull() // overdue: shown on the Loans screen in red instead
    }

    @Test
    fun `paid emi and other people's loans give no reminder`() {
        val today = LocalDate.of(2026, 12, 5)
        assertThat(EmiReminders.due(listOf(item(paid = setOf(YearMonth.of(2026, 12)), today = today)), "u1")).isEmpty()
        assertThat(EmiReminders.due(listOf(item(owner = "u2", today = today)), "u1")).isEmpty()
    }

    @Test
    fun `key differs per stage and month`() {
        val a = EmiReminders.due(listOf(item(today = LocalDate.of(2026, 12, 4))), "u1").single()
        val b = EmiReminders.due(listOf(item(today = LocalDate.of(2026, 12, 5))), "u1").single()
        assertThat(a.key).isEqualTo("l1:2026-12:ONE_DAY")
        assertThat(b.key).isNotEqualTo(a.key)
    }
}
