package com.agniv.hisaab.alerts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlertFilterTest {
    @Test
    fun `every kind lands in All and in exactly one other chip`() {
        AlertKind.entries.forEach { kind ->
            assertThat(AlertFilter.ALL.keeps(kind)).isTrue()
            val chips = listOf(AlertFilter.SMART, AlertFilter.BUDGET, AlertFilter.OTHER).filter { it.keeps(kind) }
            assertThat(chips).hasSize(1)
        }
    }

    @Test
    fun `chips match the board`() {
        assertThat(AlertFilter.SMART.keeps(AlertKind.UNUSUAL)).isTrue()
        assertThat(AlertFilter.SMART.keeps(AlertKind.CATEGORY_JUMP)).isTrue()
        assertThat(AlertFilter.BUDGET.keeps(AlertKind.BUDGET_OVER)).isTrue()
        assertThat(AlertFilter.OTHER.keeps(AlertKind.EMI)).isTrue()
        assertThat(AlertFilter.OTHER.keeps(AlertKind.REPORT)).isTrue()
    }
}
