package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.loan.ReminderStage
import com.agniv.hisaab.core.domain.model.Udhaar
import com.agniv.hisaab.core.domain.model.UdhaarDirection
import com.agniv.hisaab.core.domain.model.Visibility
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class UdhaarRemindersTest {
    private val due = LocalDate.of(2026, 12, 10)

    private fun item(
        owner: String = "u1",
        dueDate: LocalDate? = due,
        remaining: Long = 500,
    ) = UdhaarItem(
        Udhaar(
            "d1",
            "f1",
            owner,
            UdhaarDirection.I_OWE,
            "Kirana",
            Money.ofRupees(500),
            LocalDate.of(2026, 12, 1),
            dueDate,
            null,
            null,
            Visibility.FAMILY,
            Instant.EPOCH,
        ),
        emptyList(),
        Money.ofRupees(500 - remaining),
        Money.ofRupees(remaining),
        overdue = false,
    )

    private fun stage(
        today: LocalDate,
        item: UdhaarItem = item(),
    ) = UdhaarReminders.due(listOf(item), "u1", today).singleOrNull()?.stage

    @Test
    fun `reminds the day before and on the day only`() {
        assertThat(stage(due.minusDays(2))).isNull()
        assertThat(stage(due.minusDays(1))).isEqualTo(ReminderStage.ONE_DAY)
        assertThat(stage(due)).isEqualTo(ReminderStage.TODAY)
        assertThat(stage(due.plusDays(1))).isNull()
    }

    @Test
    fun `settled, undated and other people's entries give nothing`() {
        assertThat(stage(due, item(remaining = 0))).isNull()
        assertThat(stage(due, item(dueDate = null))).isNull()
        assertThat(stage(due, item(owner = "u2"))).isNull()
    }

    @Test
    fun `key changes with the stage and the due date`() {
        val a = UdhaarReminders.due(listOf(item()), "u1", due.minusDays(1)).single()
        val b = UdhaarReminders.due(listOf(item()), "u1", due).single()
        assertThat(a.key).isNotEqualTo(b.key)
        assertThat(a.key).contains("2026-12-10")
    }
}
